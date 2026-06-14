package com.defecttriage.config;

import com.defecttriage.common.*;
import com.defecttriage.entity.*;
import com.defecttriage.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepo;
    private final DefectRepository defectRepo;
    private final KnowledgeItemRepository knowledgeRepo;
    private final StateTransitionRepository transitionRepo;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private User submitter, engineer, qa;

    public DataInitializer(UserRepository userRepo, DefectRepository defectRepo,
                           KnowledgeItemRepository knowledgeRepo,
                           StateTransitionRepository transitionRepo) {
        this.userRepo = userRepo;
        this.defectRepo = defectRepo;
        this.knowledgeRepo = knowledgeRepo;
        this.transitionRepo = transitionRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepo.count() > 0) return;

        submitter = createUser("submitter", "admin123", "张三(提交人)", UserRole.SUBMITTER);
        engineer  = createUser("engineer",  "admin123", "李四(工程师)", UserRole.ENGINEER);
        qa        = createUser("qa",        "admin123", "王五(QA)",     UserRole.QA);

        seedDrafts();
        seedReported();
        seedTriaging();
        seedAnalyzed();
        seedPlanned();
        seedInRepair();
        seedFixed();
        seedVerified();
        seedClosed();
        seedReopened();
        seedExtra();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private User createUser(String username, String password, String displayName, UserRole role) {
        User u = new User();
        u.setUsername(username); u.setPassword(passwordEncoder.encode(password));
        u.setDisplayName(displayName); u.setRole(role);
        return userRepo.save(u);
    }

    private Defect base(Defect d, String title, String desc, String phenomenon, String env,
                        String steps, String expected, String actual,
                        int sev, int ui, int bi, int freq, int wa, int rw,
                        DefectStatus status, User reporter, User assignee) {
        d.setTitle(title); d.setDescription(desc);
        d.setPhenomenon(phenomenon); d.setEnvironment(env);
        d.setReproductionSteps(steps); d.setExpectedResult(expected); d.setActualResult(actual);
        d.setSeverity(sev); d.setUserImpact(ui); d.setBusinessImpact(bi);
        d.setFrequency(freq); d.setWorkaround(wa); d.setReleaseWindow(rw);
        d.setStatus(status); d.setReporter(reporter); d.setAssignee(assignee);
        d.setPriority(calcPriority(sev, ui, bi, freq, wa, rw));
        return defectRepo.save(d);
    }

    private Defect dft(DefectStatus status, User assignee, String title, String desc,
                       String phenomenon, String env, String steps, String expected, String actual,
                       int sev, int ui, int bi, int freq, int wa, int rw) {
        return base(new Defect(), title, desc, phenomenon, env, steps, expected, actual,
                sev, ui, bi, freq, wa, rw, status, submitter, assignee);
    }

    private Defect dftBy(DefectStatus status, User reporter, User assignee, String title, String desc,
                         String phenomenon, String env, String steps, String expected, String actual,
                         int sev, int ui, int bi, int freq, int wa, int rw) {
        return base(new Defect(), title, desc, phenomenon, env, steps, expected, actual,
                sev, ui, bi, freq, wa, rw, status, reporter, assignee);
    }

    private Priority calcPriority(int sev, int ui, int bi, int freq, int wa, int rw) {
        double w = sev * 0.25 + ui * 0.20 + bi * 0.20 + freq * 0.15 + (6 - wa) * 0.10 + (6 - rw) * 0.10;
        if (w >= 3.8) return Priority.P0;
        if (w >= 3.0) return Priority.P1;
        if (w >= 2.3) return Priority.P2;
        if (w >= 1.6) return Priority.P3;
        return Priority.P4;
    }

    private void transition(Defect d, DefectStatus from, DefectStatus to, User operator, String note) {
        StateTransition st = new StateTransition();
        st.setDefectId(d.getId()); st.setFromStatus(from); st.setToStatus(to);
        st.setOperator(operator); st.setNote(note);
        st.setCreatedAt(LocalDateTime.now().minusDays(1).minusHours((long)(Math.random() * 48)));
        transitionRepo.save(st);
    }

    private void fullTransition(Defect d, DefectStatus upTo, String endNote) {
        DefectStatus[] chain = {DefectStatus.DRAFT, DefectStatus.REPORTED, DefectStatus.TRIAGING,
                DefectStatus.ANALYZED, DefectStatus.PLANNED, DefectStatus.IN_REPAIR,
                DefectStatus.FIXED, DefectStatus.VERIFIED, DefectStatus.CLOSED};
        String[] notes = {"提交缺陷", "开始分诊", "已完成分诊，开始分析",
                "根因分析完成", "制定修复方案", "开始修复",
                "修复完成，提交验证", "验证通过", "关闭缺陷"};
        User[] ops = {submitter, engineer, engineer, engineer, engineer, engineer, engineer, qa, qa};
        for (int i = 0; i < chain.length - 1; i++) {
            if (chain[i + 1] == upTo) break;
            transition(d, chain[i], chain[i + 1], ops[i], notes[i]);
        }
        DefectStatus[] all = DefectStatus.values();
        int idx = java.util.Arrays.asList(all).indexOf(upTo);
        DefectStatus prev = (idx > 0) ? all[idx - 1] : DefectStatus.DRAFT;
        User op = (upTo == DefectStatus.REPORTED || upTo == DefectStatus.REOPENED) ? submitter
                : (upTo == DefectStatus.VERIFIED || upTo == DefectStatus.CLOSED) ? qa : engineer;
        transition(d, prev, upTo, op, endNote != null ? endNote : "流转至 " + upTo);
    }

    private void addKnowledge(Defect d, String titlePrefix) {
        KnowledgeItem k1 = new KnowledgeItem();
        k1.setDefect(d); k1.setType(KnowledgeType.REGRESSION_TEST);
        k1.setTitle("[回归用例] " + titlePrefix);
        k1.setContent("每次迭代发布前，验证「" + d.getTitle() + "」相关场景，确认修复未引入回归问题。");
        k1.setStatus(KnowledgeStatus.PUBLISHED); k1.setPublishedAt(LocalDateTime.now());
        knowledgeRepo.save(k1);

        KnowledgeItem k2 = new KnowledgeItem();
        k2.setDefect(d); k2.setType(KnowledgeType.TROUBLESHOOTING);
        k2.setTitle("[排查手册] " + titlePrefix);
        k2.setContent("当线上出现类似「" + d.getTitle() + "」的问题时，优先检查相关模块的配置和日志，参考修复方案：" + (d.getFixContent() != null ? d.getFixContent() : "见缺陷详情"));
        k2.setStatus(KnowledgeStatus.PUBLISHED); k2.setPublishedAt(LocalDateTime.now());
        knowledgeRepo.save(k2);

        KnowledgeItem k3 = new KnowledgeItem();
        k3.setDefect(d); k3.setType(KnowledgeType.RISK_RULE);
        k3.setTitle("[风险规则] " + titlePrefix);
        k3.setContent("新增或修改「" + d.getAffectedModules() + "」模块代码时，需确保相关校验和防护逻辑已就位，避免同类缺陷再次引入。");
        k3.setStatus(KnowledgeStatus.PUBLISHED); k3.setPublishedAt(LocalDateTime.now());
        knowledgeRepo.save(k3);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DRAFT — 5 条   (提交人草稿，尚未正式提交)
    // ══════════════════════════════════════════════════════════════════════

    private void seedDrafts() {
        Defect d;
        d = dft(DefectStatus.DRAFT, null,
          "用户列表页筛选条件偶发重置",
          "在用户管理列表页设置筛选条件后，切换Tab再返回，筛选条件被清空",
          "用户管理列表页，设置部门+角色筛选条件，切换到其他Tab页后返回，之前设置的筛选条件全部丢失",
          "Chrome 125 / Edge 125 / Firefox 127，Windows 11，React 19前端",
          "1.进入用户管理页面\n2.设置部门='技术部'，角色='工程师'\n3.点击'角色管理'Tab\n4.点击'用户管理'Tab返回",
          "返回用户管理页面后，之前设置的筛选条件保持不变",
          "筛选条件全部重置为默认值，需要重新设置",
          3, 3, 2, 4, 2, 2);
        transition(d, DefectStatus.DRAFT, DefectStatus.DRAFT, submitter, "创建草稿");

        d = dft(DefectStatus.DRAFT, null,
          "移动端H5页面底部按钮被虚拟键盘遮挡",
          "在移动端浏览器中打开缺陷详情页，点击底部操作按钮时弹出的虚拟键盘遮挡了按钮区域",
          "iOS Safari / Android Chrome，点击'流转'按钮时，弹出的确认表单中虚拟键盘遮挡了确认/取消按钮",
          "iOS 17 Safari / Android 14 Chrome，屏幕尺寸375×812及以下",
          "1.用手机浏览器打开系统\n2.进入任意缺陷详情\n3.点击底部'流转'按钮\n4.在弹出的表单中点击输入框",
          "弹出键盘后，确认/取消按钮自动上移到可见区域",
          "按钮被键盘完全遮挡，用户无法操作，只能收起键盘后才能点击",
          4, 4, 3, 5, 1, 3);
        transition(d, DefectStatus.DRAFT, DefectStatus.DRAFT, submitter, "创建草稿");

        d = dft(DefectStatus.DRAFT, null,
          "批量操作后列表未自动刷新",
          "对缺陷列表进行批量状态变更后，列表数据没有自动刷新，需手动F5刷新页面才能看到最新状态",
          "选中3条缺陷→点击批量操作→选择'标记为已分析'→确认→列表仍显示旧状态",
          "生产环境 Chrome 125",
          "1.在缺陷列表勾选3条状态为TRIAGING的缺陷\n2.点击批量操作按钮\n3.选择'标记为已分析'\n4.点击确认执行",
          "列表自动刷新，被操作的3条缺陷状态更新为ANALYZED",
          "列表无变化，手动刷新页面后才显示新状态",
          3, 3, 3, 2, 3, 2);
        transition(d, DefectStatus.DRAFT, DefectStatus.DRAFT, submitter, "创建草稿");

        d = dft(DefectStatus.DRAFT, null,
          "附件预览大图时浏览器卡死",
          "点击附件缩略图预览大图时，浏览器CPU占用飙升至100%，页面无响应约15秒",
          "附件为8000×6000分辨率PNG截图，点击预览后浏览器卡死",
          "Chrome 125 / Edge 125，Windows 11，内存16GB",
          "1.上传一张高分辨率(8000×6000)PNG截图作为附件\n2.在缺陷详情页点击该附件缩略图\n3.等待预览弹窗出现",
          "弹窗在1-2秒内显示大图预览，可正常缩放和拖拽",
          "浏览器卡死约15秒后弹窗出现，期间无法进行任何操作",
          4, 4, 3, 3, 2, 3);
        transition(d, DefectStatus.DRAFT, DefectStatus.DRAFT, submitter, "创建草稿");

        d = dft(DefectStatus.DRAFT, null,
          "通知中心未读计数与实际不符",
          "顶部导航栏通知铃铛的未读数字显示5条，但点击进入通知中心后实际只有2条未读消息",
          "未读计数与通知中心内实际未读数不一致",
          "全平台复现，Chrome 125 / Firefox 127，生产环境",
          "1.等待接收多条通知\n2.查看顶栏铃铛显示的未读数\n3.点击铃铛进入通知中心\n4.对比未读消息数量",
          "铃铛未读数字与通知中心内未读消息数一致",
          "铃铛显示5条未读，通知中心内只有2条已读和1条未读",
          2, 3, 1, 2, 4, 2);
        transition(d, DefectStatus.DRAFT, DefectStatus.DRAFT, submitter, "创建草稿");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  REPORTED — 12 条   (已提交，等待工程师分诊)
    // ══════════════════════════════════════════════════════════════════════

    private void seedReported() {
        Defect d;
        d = dft(DefectStatus.REPORTED, null,
          "登录页面验证码图片不显示",
          "登录页面验证码区域显示裂图图标，用户无法看到验证码，导致无法登录",
          "访问登录页面，验证码图片位置显示为裂图占位符",
          "Chrome 125 / Edge 125 / Firefox 127，生产环境",
          "1.打开浏览器访问登录页面\n2.观察验证码区域",
          "验证码图片正常显示，用户可识别并输入",
          "验证码位置显示为裂图图标，右键查看图片返回404",
          5, 5, 5, 5, 1, 5);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");

        d = dft(DefectStatus.REPORTED, null,
          "数据看板统计数字延迟更新超过2小时",
          "首页统计看板展示的缺陷总数/各状态数量与缺陷列表实际数据不一致，延迟超过2小时",
          "上午10点新增3条缺陷，看板的'总缺陷数'到12点才更新",
          "生产环境，Chrome 125",
          "1.查看首页统计看板的'总缺陷数'\n2.新创建一条缺陷\n3.返回首页观察看板数字",
          "看板数据应在30秒内反映最新状态",
          "新创建的缺陷在看板中超过2小时未体现",
          4, 4, 4, 3, 1, 4);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");

        d = dft(DefectStatus.REPORTED, null,
          "缺陷列表排序条件切换后分页重置到第1页",
          "在缺陷列表第3页按优先级排序后，排序条件变了但页面跳回了第1页，用户丢失了浏览位置",
          "在缺陷列表浏览到第3页时，点击列头切换排序方式，页面自动跳回第1页",
          "Chrome 125 / Firefox 127，生产环境",
          "1.进入缺陷列表，翻到第3页\n2.点击'优先级'列头切换排序\n3.观察页面跳转",
          "排序切换后保持当前页码不变",
          "排序切换后自动跳回第1页",
          2, 3, 1, 3, 4, 3);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");

        d = dft(DefectStatus.REPORTED, null,
          "缺陷创建弹窗在Safari浏览器偶发白屏",
          "在Safari浏览器点击'创建缺陷'按钮后，弹窗打开但内容区域为空白，只能看到遮罩层",
          "Safari浏览器下点击创建缺陷，弹窗背景显示但表单内容不渲染",
          "Safari 17.5，macOS Sonoma 14.5",
          "1.使用Safari浏览器打开系统\n2.点击右下角'+'创建缺陷按钮\n3.等待弹窗动画完成",
          "弹窗内正常显示缺陷创建表单",
          "弹窗内容区域为空白，控制台无报错",
          4, 4, 2, 3, 1, 3);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");

        d = dft(DefectStatus.REPORTED, null,
          "注册成功后的欢迎邮件未收到",
          "新用户注册成功后系统提示'已发送欢迎邮件'，但用户邮箱（QQ邮箱/163邮箱）未收到任何邮件",
          "用QQ邮箱注册账号，注册成功后提示邮件已发送，但收件箱（含垃圾邮件）均未收到",
          "所有浏览器，生产环境",
          "1.使用QQ邮箱地址注册新账号\n2.完成注册表单提交\n3.查看注册成功提示\n4.登录QQ邮箱检查收件箱和垃圾箱",
          "注册成功后5分钟内收到欢迎邮件",
          "注册成功后超过30分钟未收到欢迎邮件",
          3, 3, 1, 2, 2, 2);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");

        d = dft(DefectStatus.REPORTED, null,
          "附件拖拽上传区域不支持文件夹拖放",
          "附件上传区域的拖拽功能只支持单个文件，拖放文件夹时无任何反应，也未给出提示",
          "将包含多个截图文件的文件夹从资源管理器拖入上传区域，无任何反馈",
          "Windows 11，Chrome 125 / Edge 125",
          "1.在资源管理器中准备一个包含5张截图的文件夹\n2.将该文件夹拖入缺陷详情页的附件上传区域\n3.观察页面反应",
          "弹出提示'不支持文件夹上传，请选择单个文件'或自动提取文件夹内所有文件",
          "页面无任何反应，文件夹无法上传",
          2, 2, 1, 2, 4, 2);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");

        d = dft(DefectStatus.REPORTED, null,
          "搜索输入中文时页面频繁闪烁",
          "在搜索框使用中文输入法输入时，每次按键都触发页面重新渲染，导致列表闪烁",
          "使用搜狗输入法在搜索框输入中文关键词，每按一个字母列表区域就闪一下",
          "Windows 11，搜狗输入法 / 微软拼音，Chrome 125",
          "1.在缺陷列表页点击搜索框\n2.切换到中文输入法\n3.输入'登录页面'观察列表区域",
          "搜索仅在确认输入(回车或选词完成)后触发，输入过程中列表不闪烁",
          "每输入一个拼音字母列表就闪烁刷新一次",
          2, 3, 1, 5, 4, 3);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");

        d = dft(DefectStatus.REPORTED, null,
          "用户头像上传裁剪后变形",
          "个人设置页上传头像时，裁剪工具裁剪出来的头像比例异常，显示为被压扁的椭圆",
          "上传一张正方形照片，使用裁剪工具裁剪为圆形，保存后显示为横向压扁的椭圆",
          "Chrome 125 / Safari 17.5，全平台",
          "1.进入个人设置页面\n2.点击头像编辑\n3.上传一张600×600的正方形照片\n4.使用裁剪工具裁剪\n5.点击保存",
          "头像按裁剪区域等比例显示为圆形",
          "头像显示为横向压扁的椭圆形，人物面部变形",
          2, 2, 1, 2, 3, 2);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");

        d = dft(DefectStatus.REPORTED, null,
          "缺陷详情Sheet面板在滚动时顶部标题栏消失",
          "打开缺陷详情Sheet面板后向下滚动查看内容，顶部的缺陷标题和状态标签随滚动消失，无法快速确认当前查看的是哪条缺陷",
          "Sheet面板内容较长时，向下滚动后顶部标题栏完全不可见",
          "Chrome 125，Windows 11",
          "1.打开一条内容较长的缺陷详情Sheet\n2.向下滚动到修复方案区域\n3.观察顶部标题栏",
          "标题栏固定在Sheet面板顶部，始终可见(sticky header)",
          "标题栏随内容滚动消失，需要滚回顶部才能看到标题和状态",
          2, 3, 2, 4, 3, 3);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");

        d = dft(DefectStatus.REPORTED, null,
          "知识库搜索不支持模糊匹配",
          "知识库搜索仅支持精确匹配，输入'登录'搜不到标题为'登录页面样式修复'的知识条目",
          "知识库列表页搜索'登录'关键词，返回0条结果，但存在包含'登录'的知识条目",
          "生产环境，所有浏览器",
          "1.进入知识库页面\n2.在搜索框输入'登录'\n3.点击搜索",
          "返回所有标题或内容包含'登录'关键词的知识条目",
          "返回0条结果，只有输入完整标题才能搜到",
          3, 3, 2, 4, 3, 3);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");

        d = dft(DefectStatus.REPORTED, null,
          "API接口偶发返回500但无错误日志",
          "部分API接口偶发性返回HTTP 500，但应用日志中无对应错误堆栈，排查困难",
          "/api/defects接口偶发返回500 Internal Server Error，查看应用日志/Sentry均无对应错误记录",
          "生产环境，Spring Boot 3.4 + JDK 21",
          "1.持续请求/api/defects接口\n2.观察HTTP状态码\n3.出现500时立即查看应用日志",
          "所有异常被正确捕获并记录到日志",
          "部分500响应无任何日志记录",
          5, 4, 4, 3, 1, 4);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");

        d = dft(DefectStatus.REPORTED, null,
          "列表页筛选条件'创建时间'不支持自定义日期范围",
          "缺陷列表的创建时间筛选只提供了'今天/本周/本月/今年'4个预设选项，无法选择自定义日期范围",
          "用户想筛选2026年5月1日至5月31日创建的缺陷，但筛选条件不支持自定义范围",
          "全平台，生产环境",
          "1.进入缺陷列表\n2.点击'创建时间'筛选\n3.查看可选选项",
          "除了预设选项外，提供自定义日期范围选择器",
          "只有4个预设选项，无法自定义日期范围",
          3, 3, 2, 4, 2, 3);
        fullTransition(d, DefectStatus.REPORTED, "缺陷已正式提交，等待分诊");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TRIAGING — 10 条   (分诊中)
    // ══════════════════════════════════════════════════════════════════════

    private void seedTriaging() {
        Defect d;
        d = dft(DefectStatus.TRIAGING, engineer,
          "缺陷优先级自动计算权重不合理",
          "当'规避方案'分数为1（无规避方案）但'严重程度'为2（较低）时，自动计算的优先级偏高为P1",
          "severity=2, userImpact=2, businessImpact=2, frequency=2, workaround=1, releaseWindow=3 → 计算结果P1，但实际应为P2",
          "全平台",
          "1.创建一条severity较低的缺陷\n2.将workaround设为1（无规避方案）\n3.观察自动计算的优先级",
          "优先级计算公式应合理反映各维度权重，不会因单个低分项导致优先级过高",
          "workaround=1将优先级拉高到P1，不符合业务预期",
          3, 3, 3, 2, 2, 3);
        fullTransition(d, DefectStatus.TRIAGING, "已分配工程师进行分诊");

        d = dft(DefectStatus.TRIAGING, engineer,
          "前端打包后静态资源404",
          "生产构建(npm run build)后部署到Nginx，部分chunk文件路径错误导致404",
          "执行npm run build后，dist目录下部分JS chunk文件名与index.html引用不匹配",
          "Vite 8 + Nginx 1.26，生产环境",
          "1.执行npm run build\n2.将dist目录部署到Nginx\n3.访问应用首页",
          "所有静态资源正确加载，应用正常运行",
          "控制台报多个chunk JS文件404错误，页面白屏",
          5, 5, 5, 5, 1, 5);
        fullTransition(d, DefectStatus.TRIAGING, "已分配工程师进行分诊");

        d = dft(DefectStatus.TRIAGING, engineer,
          "数据库连接池在高并发下耗尽",
          "压测500并发时，HikariCP连接池耗尽导致请求排队超时",
          "JMeter 500线程并发压测/api/defects接口，约30秒后大量请求超时",
          "生产环境，MySQL 8.0 + HikariCP",
          "1.配置JMeter 500并发线程\n2.持续压测/api/defects接口\n3.观察应用日志和数据库连接数",
          "连接池配置合理，高并发下请求正常处理",
          "连接池耗尽，大量请求抛出Connection is not available异常",
          5, 5, 5, 3, 1, 5);
        fullTransition(d, DefectStatus.TRIAGING, "已分配工程师进行分诊");

        d = dft(DefectStatus.TRIAGING, engineer,
          "移动端表格横向滚动体验差",
          "在手机浏览器中查看缺陷列表，表格列数较多时需要横向滚动，但滚动条不明显且不支持手势滑动",
          "iPhone 15 Pro Safari，打开缺陷列表后需要横向滑动查看优先级/状态等列，但无法用滑动手势操作",
          "iOS Safari / Android Chrome，屏幕宽度小于768px",
          "1.用手机浏览器打开缺陷列表\n2.尝试横向滑动查看更多列",
          "表格支持手势横向滑动，或响应式折叠次要列",
          "无法手势滑动，必须精准拖到底部滚动条才能横向移动",
          3, 4, 2, 5, 3, 3);
        fullTransition(d, DefectStatus.TRIAGING, "已分配工程师进行分诊");

        d = dft(DefectStatus.TRIAGING, engineer,
          "用户登出后JWT Token未失效",
          "用户点击退出登录后，前端清除了Token但后端未将其加入黑名单，该Token在有效期内仍可访问API",
          "退出登录后，用之前保存的JWT Token重新请求API，仍然返回200 OK",
          "全平台，JWT认证机制",
          "1.登录系统，从浏览器DevTools复制JWT Token\n2.点击退出登录\n3.用Postman携带该Token请求/api/defects",
          "退出后Token立即失效，使用该Token请求API返回401",
          "Token在有效期内仍可正常访问API",
          4, 3, 4, 2, 1, 4);
        fullTransition(d, DefectStatus.TRIAGING, "已分配工程师进行分诊");

        d = dft(DefectStatus.TRIAGING, engineer,
          "缺陷流转确认弹窗在暗色模式下文字不可见",
          "系统暗色模式下，流转确认弹窗的背景和文字颜色对比度不足，内容几乎不可见",
          "切换到暗色模式，点击流转按钮，弹窗中的标题和描述文字与深色背景融为一体",
          "Chrome 125，Windows 11，系统暗色模式",
          "1.将操作系统切换为暗色模式\n2.打开系统\n3.进入缺陷详情\n4.点击流转按钮",
          "弹窗在暗色模式下文字清晰可见",
          "弹窗文字颜色与背景颜色相近，几乎不可读",
          3, 3, 2, 4, 3, 2);
        fullTransition(d, DefectStatus.TRIAGING, "已分配工程师进行分诊");

        d = dft(DefectStatus.TRIAGING, engineer,
          "大列表分页性能：翻到第50页后响应极慢",
          "缺陷列表数据量大时(500条+)，翻到第50页后页面响应时间超过5秒",
          "列表共550条缺陷，每页10条，翻到第50页时等待超过5秒才有响应",
          "Chrome 125，生产环境",
          "1.向数据库插入550条缺陷\n2.进入缺陷列表\n3.快速连续点击分页跳转到第50页",
          "分页跳转在1秒内完成",
          "翻到第50页时响应时间超过5秒",
          3, 3, 2, 2, 3, 2);
        fullTransition(d, DefectStatus.TRIAGING, "已分配工程师进行分诊");

        d = dft(DefectStatus.TRIAGING, engineer,
          "Sheet面板打开时URL参数更新延迟",
          "点击缺陷列表中的某个缺陷，Sheet面板已打开但浏览器URL栏的?defect=参数约2秒后才更新",
          "点击缺陷行，Sheet面板立即从右侧滑出，但URL参数延迟约2秒才从?defect=52变为?defect=68",
          "Chrome 125 / Safari 17.5",
          "1.打开缺陷列表\n2.点击某条缺陷打开Sheet\n3.立即观察浏览器URL栏",
          "URL参数与Sheet面板同步更新",
          "URL参数延迟约2秒更新",
          2, 2, 1, 3, 3, 2);
        fullTransition(d, DefectStatus.TRIAGING, "已分配工程师进行分诊");

        d = dft(DefectStatus.TRIAGING, engineer,
          "统计栏进度环在窗口缩放时变形",
          "调整浏览器窗口大小时，首页统计栏的SVG进度环变形为椭圆",
          "将浏览器窗口从1920×1080缩小到1024×768，统计栏的进度环不再是正圆",
          "Chrome 125 / Firefox 127，Windows 11",
          "1.打开系统首页\n2.调整浏览器窗口宽度从1920到1024\n3.观察统计栏进度环形状",
          "进度环始终保持正圆形",
          "进度环随窗口缩小而变形为椭圆",
          1, 1, 1, 3, 4, 2);
        fullTransition(d, DefectStatus.TRIAGING, "已分配工程师进行分诊");

        d = dft(DefectStatus.TRIAGING, engineer,
          "缺陷标题输入框允许输入超长标题导致列表展示异常",
          "创建缺陷时标题字段无字符数限制，输入500+字符标题后，列表页标题列撑破表格布局",
          "创建缺陷时在标题字段粘贴500字长文本，提交后在列表页显示时标题撑破单元格宽度",
          "全平台",
          "1.点击创建缺陷\n2.在标题栏粘贴500字长文本\n3.提交缺陷\n4.进入列表页查看",
          "标题字段有合理字符数限制（如100字），超出时截断并提示",
          "超长标题撑破列表表格布局",
          2, 3, 1, 2, 4, 3);
        fullTransition(d, DefectStatus.TRIAGING, "已分配工程师进行分诊");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ANALYZED — 12 条   (分析完成)
    // ══════════════════════════════════════════════════════════════════════

    private void seedAnalyzed() {
        Defect d;
        d = dft(DefectStatus.ANALYZED, engineer,
          "Token刷新接口被频繁调用导致服务器CPU飙升",
          "前端Axios拦截器在Token即将过期时不断调用refresh接口，导致refresh接口QPS异常高",
          "生产环境监控发现/refresh接口QPS达到500+，CPU占用80%",
          "生产环境，Spring Boot 3.4",
          "1.登录系统\n2.查看浏览器Network面板\n3.观察/refresh接口调用频率",
          "Token刷新有合理的节流机制，不会频繁调用",
          "/refresh接口每秒被调用数十次",
          4, 3, 3, 5, 1, 4);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("Axios拦截器在Token到期前5分钟即开始尝试刷新，但刷新失败后未设置重试间隔，导致无限快速重试");

        d = dft(DefectStatus.ANALYZED, engineer,
          "导出功能单元格内换行符导致CSV格式错乱",
          "导出缺陷列表为CSV时，部分缺陷描述字段包含换行符\\n，导致CSV文件格式错乱，Excel打开后数据错列",
          "缺陷描述字段包含多行文本，导出CSV后用Excel打开，后续所有列数据错位",
          "Excel 365 / WPS，Windows 11",
          "1.创建一条描述包含多行文本的缺陷\n2.导出缺陷列表为CSV\n3.用Excel打开CSV文件",
          "CSV中换行符被正确转义，Excel打开后数据显示正常",
          "数据严重错列",
          4, 4, 3, 3, 2, 3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("CSV导出工具未对字段内的换行符和逗号做转义处理，直接用String拼接");

        d = dft(DefectStatus.ANALYZED, engineer,
          "页面加载时出现短暂白屏后闪出内容",
          "刷新页面或首次加载时，页面先白屏约1-2秒后才渲染内容，体验类似于'闪白'",
          "刷新缺陷列表页时，浏览器白屏约1.5秒后才显示列表内容",
          "Chrome 125，生产环境",
          "1.清除浏览器缓存\n2.访问系统\n3.观察页面加载过程",
          "页面加载时显示骨架屏或Loading动画，无白屏",
          "白屏1.5秒后内容突然出现",
          2, 3, 2, 5, 3, 3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("React Suspense未配置fallback组件，JS bundle加载期间无任何UI渲染");

        d = dft(DefectStatus.ANALYZED, engineer,
          "同一账号在多设备同时登录无互踢机制",
          "同一个账号可以在Chrome、Edge、Safari上同时登录操作，无任何互踢或安全提示",
          "用engineer账号在Chrome和Firefox上同时登录，两边都能正常操作",
          "全平台",
          "1.在Chrome上用engineer账号登录\n2.在Firefox上用同一账号登录\n3.在两边分别操作",
          "后登录的设备提示'该账号已在其他设备登录，是否踢下线'或将原设备自动登出",
          "两个设备都能同时正常操作，无任何提示",
          3, 3, 4, 2, 1, 3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("JWT Token生成时未绑定设备/Session标识，后端也未维护活跃Session列表");

        d = dft(DefectStatus.ANALYZED, engineer,
          "附件上传进度条在慢网络下卡住不动",
          "在3G慢网络环境下上传5MB附件，进度条走到30%后卡住不动，约30秒后直接跳到100%",
          "切换Chrome DevTools网络限速为Slow 3G，上传5MB图片，进度条从30%直接跳到100%",
          "Chrome 125，Slow 3G网络",
          "1.打开Chrome DevTools→Network→限速Slow 3G\n2.上传5MB附件\n3.观察进度条变化",
          "进度条平滑增长，真实反映上传进度",
          "进度条阶段性卡住，然后突然跳到100%",
          2, 2, 1, 2, 4, 2);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("XMLHttpRequest的progress事件触发频率过低，前端未做进度平滑处理");

        d = dft(DefectStatus.ANALYZED, engineer,
          "关联缺陷下拉列表加载缓慢且不支持搜索",
          "创建缺陷时选择'关联缺陷'的下拉列表一次性加载全部500+条缺陷，加载时间超过3秒，且不支持搜索",
          "点击关联缺陷下拉框，等待3秒后弹出一个500+条缺陷的列表，需要手动滚动找到目标",
          "生产环境，Chrome 125",
          "1.点击创建缺陷\n2.点击'关联缺陷'下拉框\n3.观察加载时间",
          "下拉列表支持远程搜索，输入关键词后异步加载匹配结果",
          "一次性加载全部数据，加载慢且查找困难",
          3, 3, 2, 4, 3, 3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("前端Select组件未实现虚拟滚动和远程搜索，将全部缺陷数据一次性加载到DOM");

        d = dft(DefectStatus.ANALYZED, engineer,
          "英文/数字连续字符不换行导致表格撑宽",
          "缺陷标题或描述中包含长URL或连续英文字符串时，内容不自动换行，撑破表格列宽",
          "缺陷描述中包含一段很长的API URL（约200字符），表格列被撑开到远超屏幕宽度",
          "Chrome 125 / Safari 17.5",
          "1.创建一条描述包含长URL的缺陷\n2.进入缺陷列表\n3.观察表格列宽",
          "长URL在单元格内自动换行，不撑破表格布局",
          "长URL撑破单元格宽度，出现横向滚动条",
          2, 2, 1, 3, 4, 2);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("CSS中缺少word-break:break-all或overflow-wrap:break-word属性");

        d = dft(DefectStatus.ANALYZED, engineer,
          "系统时间显示UTC时间而非北京时间",
          "所有页面的时间显示均为UTC时间，与北京时间差8小时",
          "缺陷列表的'创建时间'列显示为10:30，但实际北京时间是18:30",
          "全平台，生产环境",
          "1.创建一条缺陷\n2.进入缺陷列表\n3.查看创建时间列",
          "时间显示为北京时间(UTC+8)",
          "时间显示为UTC时间，比实际时间少8小时",
          3, 4, 3, 5, 2, 4);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("后端LocalDateTime序列化时未配置时区，Jackson默认使用UTC；前端moment/dayjs也未做时区转换");

        d = dft(DefectStatus.ANALYZED, engineer,
          "用户权限变更后不刷新页面仍显示旧菜单",
          "管理员修改用户角色后，该用户在不刷新页面的情况下看不到新的菜单项",
          "管理员将submitter角色改为engineer，该用户继续操作时仍只看到submitter的菜单",
          "全平台",
          "1.管理员修改用户A的角色\n2.用户A不刷新页面继续操作\n3.观察菜单变化",
          "角色变更后前端自动感知并更新菜单",
          "菜单不变，需手动刷新页面才能看到新菜单",
          3, 3, 2, 2, 2, 3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("用户角色信息在登录时一次性加载到AuthContext，无定期刷新或推送更新机制");

        d = dft(DefectStatus.ANALYZED, engineer,
          "批量操作缺少全选当前页功能",
          "缺陷列表只支持逐个勾选，没有'全选当前页'/'全选所有页'功能，批量操作效率低",
          "列表中每页10条，想批量操作50条缺陷需要手动勾选50次",
          "全平台",
          "1.进入缺陷列表\n2.查找'全选'功能\n3.尝试批量操作",
          "列表提供'全选当前页'和'全选所有页'选项",
          "只能逐个勾选，无全选功能",
          2, 3, 2, 4, 3, 3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("TanStack Table的rowSelection已实现但UI层未暴露全选复选框");

        d = dft(DefectStatus.ANALYZED, engineer,
          "流转操作后旧状态数据在Sheet面板中残留",
          "执行流转操作后，Sheet面板中显示的缺陷状态已经更新，但部分字段(如根因分析)仍然显示流转前的内容",
          "缺陷从ANALYZED流转到PLANNED后，rootCause字段在Sheet中显示为空，但实际数据库中已保存",
          "Chrome 125，生产环境",
          "1.打开一条ANALYZED状态的缺陷Sheet\n2.执行流转到PLANNED\n3.关闭Sheet后重新打开",
          "Sheet面板数据与数据库状态一致",
          "部分字段显示陈旧数据",
          3, 3, 2, 2, 2, 3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("流转API返回的Defect对象未包含@ManyToOne关联的更新字段，TanStack Query缓存未失效");

        d = dft(DefectStatus.ANALYZED, engineer,
          "N+1查询：缺陷列表加载关联用户信息导致性能问题",
          "缺陷列表API在查询缺陷时，对每条缺陷的reporter/assignee/verifier分别执行单独的SQL查询",
          "缺陷列表加载100条数据时，后台执行301次SQL查询（1次查缺陷+100×3次查用户）",
          "生产环境，Spring Data JPA + Hibernate",
          "1.打开浏览器DevTools Network面板\n2.进入缺陷列表页\n3.查看后端日志SQL执行数量",
          "使用JOIN或@EntityGraph一次性加载关联数据，SQL执行数不超过5条",
          "SQL执行数量随缺陷数量线性增长",
          4, 3, 3, 5, 1, 3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("Defect实体的@ManyToOne默认FetchType.LAZY，列表查询时触发N+1问题");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PLANNED — 8 条   (修复方案已规划)
    // ══════════════════════════════════════════════════════════════════════

    private void seedPlanned() {
        Defect d;
        d = dft(DefectStatus.PLANNED, engineer,
          "HTTP安全头缺失导致安全扫描不通过",
          "安全扫描工具报告应用缺少X-Content-Type-Options、X-Frame-Options等安全响应头",
          "用OWASP ZAP扫描系统，报告6个安全头缺失",
          "全环境，Spring Boot 3.4",
          "1.使用OWASP ZAP扫描应用\n2.查看扫描报告中的安全头部分",
          "所有安全响应头正确配置",
          "缺少X-Content-Type-Options, X-Frame-Options, X-XSS-Protection等6个安全头",
          3, 2, 4, 5, 1, 3);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已规划，等待开发排期");
        d.setRootCauseHypothesis("Spring Security配置类未添加SecurityHeaders配置");
        d.setFixPlan("在SecurityConfig中添加securityHeaders配置，启用所有必需的安全响应头");

        d = dft(DefectStatus.PLANNED, engineer,
          "生产环境日志级别为DEBUG导致磁盘写满",
          "生产环境日志级别配置为DEBUG，导致日志文件快速增长，一天内写满服务器磁盘",
          "生产服务器磁盘使用率告警，检查发现application.log单个文件达到50GB",
          "生产环境，Spring Boot 3.4 + Logback",
          "1.部署应用后正常运行\n2.24小时后检查磁盘使用率\n3.查看日志文件大小",
          "生产环境日志级别为INFO或WARN，日志量在可控范围",
          "DEBUG级别日志产生大量SQL和请求日志，一天写满磁盘",
          5, 3, 5, 5, 1, 5);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已规划，等待开发排期");
        d.setRootCauseHypothesis("application.properties中logging.level.root设置为DEBUG，生产部署时未覆盖");
        d.setFixPlan("将生产环境日志级别改为WARN，关键业务日志INFO，同时配置日志滚动策略和大小限制");

        d = dft(DefectStatus.PLANNED, engineer,
          "附件下载文件名中文乱码",
          "下载附件时，文件名中的中文字符在浏览器下载弹窗中显示为乱码",
          "附件文件名为'测试报告.docx'，下载时浏览器显示为'æµè¯æ¥å.docx'",
          "Chrome 125 / Edge 125，生产环境",
          "1.上传一个中文文件名的附件\n2.在缺陷详情页点击下载该附件\n3.观察浏览器下载弹窗中的文件名",
          "下载文件名正确显示中文",
          "中文文件名显示为乱码",
          3, 3, 2, 4, 2, 3);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已规划，等待开发排期");
        d.setRootCauseHypothesis("Content-Disposition响应头中filename参数未进行URL编码");
        d.setFixPlan("在FileService下载方法中对filename进行URLEncoder.encode处理，并设置filename*=UTF-8''");

        d = dft(DefectStatus.PLANNED, engineer,
          "缺陷创建时附件上传失败无明确错误信息",
          "创建缺陷时同时上传附件，上传失败后只显示'上传失败'，不告知具体原因（文件过大/类型不支持/网络错误）",
          "上传一个6MB文件（超过5MB限制），只提示'上传失败'",
          "Chrome 125，生产环境",
          "1.点击创建缺陷\n2.选择一个6MB的文件上传\n3.观察错误提示",
          "根据失败原因给出具体提示，如'文件大小超过5MB限制'",
          "只显示'上传失败'，不知道具体原因",
          2, 3, 1, 2, 4, 2);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已规划，等待开发排期");
        d.setRootCauseHypothesis("后端FileService统一捕获异常后返回通用错误消息，未区分具体失败原因");
        d.setFixPlan("在FileService中针对不同异常类型返回具体错误信息，前端toast展示详细原因");

        d = dft(DefectStatus.PLANNED, engineer,
          "CSS文件加载失败导致页面完全无样式",
          "CDN或网络问题导致CSS文件加载失败后，页面显示为完全无样式的纯文字，连基本布局都没有",
          "模拟CSS文件请求返回404，页面显示为裸露的HTML，没有任何样式",
          "Chrome 125，生产环境",
          "1.使用Chrome DevTools阻止CSS文件加载\n2.刷新页面\n3.观察页面显示",
          "CSS加载失败时至少有内联关键CSS保证基本布局可用",
          "页面完全无样式，体验极差",
          2, 3, 2, 2, 3, 2);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已规划，等待开发排期");
        d.setRootCauseHypothesis("所有CSS依赖外部CSS文件，未抽取Critical CSS内联到HTML中");
        d.setFixPlan("将首屏Critical CSS内联到index.html的style标签中，确保CSS加载失败时基本布局可用");

        d = dft(DefectStatus.PLANNED, engineer,
          "Toast通知堆叠时用户难以逐个关闭",
          "短时间内触发多条Toast通知时，它们垂直堆叠在屏幕右上角，用户无法快速关闭中间某条，只能等自动消失",
          "连续创建3条缺陷后，3条成功Toast堆叠显示，持续5秒，挡住页面右上角操作区",
          "全平台",
          "1.连续快速执行多个操作触发Toast\n2.尝试关闭中间某条Toast\n3.观察交互体验",
          "Toast支持手动点击关闭，或堆叠时自动合并为'完成了3项操作'",
          "多条Toast堆叠遮挡操作区域，只能等待自动消失",
          1, 2, 1, 3, 4, 2);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已规划，等待开发排期");
        d.setRootCauseHypothesis("Toast组件无队列管理和合并机制，每条通知独立渲染");
        d.setFixPlan("实现Toast队列：同类Toast在2秒内连续触发时合并计数，超过3条时只显示最新1条+展开按钮");

        d = dft(DefectStatus.PLANNED, engineer,
          "IE11浏览器访问时页面完全白屏无降级提示",
          "虽然系统不要求支持IE11，但IE用户访问时页面完全白屏，连'请使用现代浏览器'的提示都没有",
          "使用IE11打开系统，页面白屏，没有任何提示信息",
          "IE 11，Windows 10",
          "1.使用IE11浏览器打开系统\n2.观察页面显示",
          "显示'您的浏览器版本过低，请使用Chrome/Edge/Firefox等现代浏览器'的友好提示",
          "页面完全白屏，无任何提示",
          1, 1, 2, 1, 3, 1);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已规划，等待开发排期");
        d.setRootCauseHypothesis("index.html中无浏览器兼容性检测脚本，Vite打包的ES模块IE完全不支持");
        d.setFixPlan("在index.html中添加<noscript>和浏览器检测脚本，不兼容时显示友好升级提示");

        d = dft(DefectStatus.PLANNED, engineer,
          "单条缺陷的状态流转历史记录不完整",
          "缺陷详情Sheet面板的流转历史Tab中，只能看到最近3条流转记录，更早的记录无法查看",
          "缺陷经历了8次流转（DRAFT→CLOSED全部状态），但流转历史只显示最后3条",
          "全平台",
          "1.查看一条CLOSED状态的缺陷详情\n2.点击'流转历史'Tab\n3.观察流转记录数量",
          "显示完整的流转历史，包含从创建到当前状态的所有状态变更",
          "只显示最近3条记录",
          2, 2, 1, 2, 3, 2);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已规划，等待开发排期");
        d.setRootCauseHypothesis("前端API调用未传分页参数或后端查询限制返回最近3条");
        d.setFixPlan("前端流转历史Tab请求时不限制数量，后端StateTransitionRepository查询添加ORDER BY createdAt DESC");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  IN_REPAIR — 15 条   (修复中)
    // ══════════════════════════════════════════════════════════════════════

    private void seedInRepair() {
        Defect d;
        d = dft(DefectStatus.IN_REPAIR, engineer,
          "密码修改后未发送通知邮件",
          "用户在个人设置中修改密码后，系统未发送'密码已修改'的通知邮件，存在安全风险",
          "修改密码后，绑定的邮箱未收到密码变更通知",
          "生产环境",
          "1.登录系统\n2.进入个人设置\n3.修改密码\n4.检查绑定邮箱",
          "修改密码后5分钟内收到密码变更通知邮件",
          "未收到任何通知邮件",
          3, 3, 4, 2, 2, 3);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("AuthService.changePassword方法中缺少邮件发送逻辑");
        d.setFixPlan("在changePassword方法中添加异步邮件发送调用");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "表格列宽拖拽调整后刷新页面未保存",
          "用户在缺陷列表中手动拖拽调整了列宽，但刷新页面后列宽恢复默认值",
          "将'标题'列拖宽到300px，刷新页面后恢复到默认150px",
          "Chrome 125，生产环境",
          "1.进入缺陷列表\n2.拖拽调整'标题'列宽到300px\n3.刷新页面\n4.观察列宽",
          "列宽调整自动保存到localStorage，刷新后保持",
          "列宽恢复默认值",
          1, 2, 1, 3, 4, 2);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("TanStack Table列宽调整未与localStorage持久化集成");
        d.setFixPlan("监听columnSizing变化事件，写入localStorage，初始化时从localStorage读取");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "用户连续快速点击创建按钮导致重复创建",
          "快速双击或连续点击'创建缺陷'提交按钮时，前端未做防抖处理，导致创建了多条完全相同的缺陷",
          "提交按钮点击后约1秒才有响应，期间用户又点击了两次，导致创建了3条相同缺陷",
          "Chrome 125，生产环境",
          "1.点击创建缺陷\n2.在提交按钮上快速连续点击3次\n3.进入列表查看",
          "按钮点击后立即禁用，防止重复提交",
          "创建了3条完全相同的缺陷",
          4, 3, 3, 3, 2, 3);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("提交按钮未设置loading状态和disabled属性，无防重复提交机制");
        d.setFixPlan("提交按钮点击后立即设置disabled=true并显示loading状态，API返回后恢复");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "敏感信息明文出现在前端JS代码中",
          "前端打包后的JS文件中包含DeepSeek API密钥的明文引用",
          "查看dist/assets/index-xxx.js文件，发现sk-b28baac32f224155bbe69844264c4ec3明文硬编码",
          "生产环境，Vite 8",
          "1.打开浏览器DevTools\n2.查看Sources面板中的JS文件\n3.搜索sk-关键字",
          "API密钥仅存在于后端配置，前端代码中无任何密钥信息",
          "前端JS文件中包含API密钥明文",
          5, 2, 5, 5, 1, 4);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("DeepSeek API密钥被错误地配置在Vite环境变量VITE_DEEPSEEK_KEY中并被前端代码引用");
        d.setFixPlan("移除前端所有DeepSeek调用代码，AI建议统一通过后端API代理调用");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "图表tooltip在页面边缘被截断",
          "统计栏色条的百分比tooltip在页面右侧边缘位置时，部分内容超出视口被截断",
          "鼠标悬停在最右侧色条上，tooltip右半部分超出浏览器窗口被截断",
          "Chrome 125 / Firefox 127，1920×1080",
          "1.将浏览器窗口调整为1366×768\n2.鼠标悬停统计栏最右侧色条\n3.观察tooltip显示",
          "tooltip自动调整位置，始终完全可见",
          "tooltip超出视口被截断",
          1, 2, 1, 2, 4, 2);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("Tooltip定位使用fixed+right计算，未考虑视口边界碰撞检测");
        d.setFixPlan("添加tooltip边界检测逻辑，超出右边界时向左偏移，超出下边界时向上偏移");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "登录后重定向到原始目标页面丢失",
          "未登录用户访问/defects/123?defect=123，登录后跳转到首页而非原始URL",
          "未登录状态下访问列表某个缺陷的分享链接，登录成功后跳转到了首页",
          "全平台",
          "1.退出登录\n2.在浏览器输入完整URL: /?defect=50\n3.系统重定向到登录页\n4.输入账号密码登录",
          "登录成功后跳转回/?defect=50",
          "登录成功后跳转到首页",
          2, 3, 2, 3, 3, 3);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("登录页的redirect参数在TanStack Router导航过程中丢失");
        d.setFixPlan("在登录组件中从URL search params读取redirect参数，登录成功后使用router.navigate跳转");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "表单校验错误提示位置不准确",
          "创建缺陷表单中，某个字段校验失败时错误提示显示在相邻字段下方，用户困惑",
          "在'期望结果'字段输入超长文本后，错误提示出现在下方'实际结果'字段底部",
          "Chrome 125，生产环境",
          "1.点击创建缺陷\n2.在期望结果字段输入10000字\n3.在现象描述字段留空\n4.点击提交",
          "每个字段的错误提示紧邻该字段下方",
          "错误提示与对应字段错位",
          2, 2, 1, 2, 4, 2);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("表单校验错误使用全局error对象而非字段级校验，且渲染位置使用了错误的DOM锚点");
        d.setFixPlan("改为字段级校验，每个FormField组件渲染自己的错误信息");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "用户注册时密码强度校验缺失",
          "注册页面密码字段只校验了非空，未校验密码强度（长度/大小写/数字/特殊字符）",
          "可以注册密码为'123'的账号",
          "全平台",
          "1.进入注册页面\n2.用户名填写'test'\n3.密码填写'123'\n4.点击注册",
          "密码至少8位，包含大小写字母+数字+特殊字符中的至少2种",
          "密码'123'注册成功",
          3, 2, 4, 2, 2, 2);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("注册表单前端和后端均缺少密码强度校验规则");
        d.setFixPlan("前端添加密码强度正则校验+实时强度指示条，后端AuthService.register添加相同校验");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "附件预览不支持PDF文件",
          "上传PDF附件后，点击预览按钮无反应，控制台报错'Unsupported format'",
          "上传一份PDF格式的技术文档，在缺陷详情页点击预览，无任何反应",
          "Chrome 125 / Firefox 127，生产环境",
          "1.上传一个PDF文件\n2.在缺陷详情Sheet点击该附件\n3.观察预览效果",
          "支持PDF文件在线预览",
          "PDF无法预览，无任何反馈",
          3, 3, 2, 3, 2, 2);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("前端文件预览组件仅支持image/*类型，未集成PDF.js等PDF预览方案");
        d.setFixPlan("集成pdfjs-dist，对PDF类型附件使用<iframe>或<canvas>渲染预览");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "API文档缺失导致前端联调效率低",
          "后端API未集成Swagger/Knife4j文档，前端开发需要逐个人问接口定义",
          "前端开发想查看/api/defects接口的请求参数和响应结构，但找不到任何API文档",
          "开发环境",
          "1.启动后端服务\n2.访问/swagger-ui.html或/doc.html\n3.查看API文档",
          "可访问在线API文档，查看所有接口定义",
          "无API文档入口",
          2, 2, 2, 4, 3, 2);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("项目pom.xml中未引入springdoc-openapi依赖，无Swagger自动配置");
        d.setFixPlan("在pom.xml中添加springdoc-openapi-starter-webmvc-ui依赖，创建OpenAPIConfig配置类");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "过滤后的列表导出为全部数据而非过滤结果",
          "用户筛选缺陷列表（如只看P0缺陷）后点击导出，导出的CSV包含全部缺陷而非筛选后的结果",
          "筛选状态为REPORTED的缺陷（5条），点击导出按钮，CSV文件包含全部100条缺陷",
          "全平台",
          "1.在缺陷列表筛选状态=REPORTED\n2.点击导出按钮\n3.打开导出的CSV文件\n4.检查数据行数",
          "导出数据与当前筛选条件一致",
          "导出数据包含所有缺陷，忽略了筛选条件",
          4, 3, 3, 4, 1, 3);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("导出API未接收筛选参数，始终查询全量数据");
        d.setFixPlan("导出API接收与列表查询相同的筛选参数，后端DefectService.export方法应用筛选条件");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "cookie中没有设置SameSite属性导致CSRF风险",
          "JWT Token存储在localStorage中，部分场景也写入了Cookie，但Cookie未设置SameSite属性",
          "安全审计报告指出应用存在CSRF风险，Cookie缺少SameSite属性",
          "全环境",
          "1.登录系统\n2.打开DevTools→Application→Cookies\n3.检查Cookie属性",
          "所有Cookie设置SameSite=Strict或SameSite=Lax",
          "Cookie缺少SameSite属性",
          3, 2, 4, 5, 1, 3);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("后端设置Cookie时未调用sameSite()方法");
        d.setFixPlan("在JwtUtil和WebConfig中设置所有Cookie的SameSite=Strict属性");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "页面返回按钮行为不符合预期",
          "在缺陷详情Sheet中点击浏览器的返回按钮，期望关闭Sheet，实际是退出了整个页面",
          "打开/?defect=50，Sheet面板打开，点击浏览器返回按钮，整个页面回到了浏览器主页",
          "Chrome 125，全平台",
          "1.进入缺陷列表\n2.点击某条缺陷打开Sheet面板(?defect=ID)\n3.点击浏览器返回按钮",
          "Sheet面板关闭，URL回到/?，留在缺陷列表页",
          "整个页面返回浏览器主页/上一页",
          2, 3, 1, 4, 3, 2);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("TanStack Router的history.pushState未正确处理Sheet URL栈");
        d.setFixPlan("打开Sheet时使用router.navigate({search:{defect:id}})而非直接操作history，确保返回按钮行为正确");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "大文件上传导致后端OOM",
          "用户上传超过50MB的文件（虽然前端限制5MB，但可通过API直接调用绕过），导致Java堆内存溢出",
          "用Postman直接POST /api/files/upload上传50MB文件，服务OOM",
          "生产环境，JDK 21",
          "1.用Postman构造multipart请求\n2.上传50MB文件到/api/files/upload\n3.观察服务状态",
          "后端限制文件大小，超过限制返回413",
          "服务OOM崩溃",
          5, 3, 5, 2, 1, 4);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("Spring Boot的spring.servlet.multipart.max-file-size仅限制了前端请求，API直接调用未做大小校验");
        d.setFixPlan("在FileService.upload方法中手动校验multipartFile.getSize()，超过限制抛出异常");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "列表页排序状态未同步到URL参数",
          "在缺陷列表点击列头排序后，URL参数未更新，分享链接给同事后排序条件丢失",
          "按优先级降序排列缺陷列表后，复制URL发给同事，同事打开是默认排序",
          "全平台",
          "1.点击'优先级'列头排序\n2.复制浏览器地址栏URL\n3.在新标签页打开该URL",
          "URL包含排序参数，如?sort=priority&order=desc",
          "URL无排序参数，分享后排序丢失",
          2, 2, 1, 3, 3, 2);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("TanStack Table的sorting状态未与TanStack Router的search params双向同步");
        d.setFixPlan("监听sorting状态变化，同步更新URL search params；页面加载时从URL恢复排序状态");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "同一缺陷被多人同时编辑时无冲突提示",
          "两个工程师同时打开同一条缺陷并进行流转操作，后提交的操作静默覆盖了先提交的",
          "工程师A和B同时打开缺陷#42，A先流转到ANALYZED，B后流转到TRIAGING覆盖了A的操作",
          "全平台",
          "1.工程师A打开缺陷#42详情\n2.工程师B也打开缺陷#42详情\n3.A流转到ANALYZED\n4.B流转到TRIAGING\n5.查看缺陷当前状态",
          "后提交的流转操作提示'数据已被他人修改，请刷新后重试'",
          "后提交的操作静默覆盖了前一个操作",
          4, 3, 4, 2, 1, 3);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("Defect实体虽有@Version乐观锁字段，但流转API未捕获OptimisticLockException并提示用户");
        d.setFixPlan("在DefectService流转方法中捕获OptimisticLockException，返回409 Conflict状态码，前端弹窗提示刷新");

        d = dft(DefectStatus.IN_REPAIR, engineer,
          "暗色模式和亮色模式切换时过渡生硬",
          "切换暗色/亮色模式时页面瞬间变化，无过渡动画，体验突兀",
          "点击主题切换按钮，页面瞬间从白色背景变为黑色背景，视觉冲击感强",
          "全平台",
          "1.点击顶栏主题切换按钮\n2.观察页面变化过程",
          "主题切换有0.3s的平滑过渡动画",
          "主题瞬间切换，无过渡",
          1, 1, 1, 2, 3, 2);
        fullTransition(d, DefectStatus.IN_REPAIR, "开发人员已开始修复");
        d.setRootCauseHypothesis("CSS变量切换时未设置transition属性");
        d.setFixPlan("在:root选择器添加transition: background-color 0.3s ease, color 0.3s ease");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FIXED — 10 条   (已修复，待验证)
    // ══════════════════════════════════════════════════════════════════════

    private void seedFixed() {
        Defect d;
        d = dft(DefectStatus.FIXED, engineer,
          "Spring Boot Actuator端点暴露到公网",
          "/actuator/health、/actuator/env等端点未做访问控制，公网可直接访问",
          "curl http://服务器IP:8081/actuator/env 可直接查看环境变量，包含数据库密码等敏感信息",
          "生产环境",
          "1.从外网curl /actuator/env\n2.查看返回内容",
          "Actuator端点仅内网/认证用户可访问",
          "公网可直接访问所有Actuator端点",
          5, 2, 5, 5, 1, 5);
        fullTransition(d, DefectStatus.FIXED, "修复完成，已提交代码");
        d.setRootCauseHypothesis("application.properties中management.endpoints.web.exposure.include=*且未配置Spring Security对actuator路径的保护");
        d.setFixPlan("限制exposure仅保留health/info，并为actuator路径添加认证拦截");
        d.setFixContent("修改application.properties: management.endpoints.web.exposure.include=health,info；SecurityConfig添加.antMatchers(\"/actuator/**\").authenticated()");
        d.setAffectedModules("SecurityConfig, application.properties");
        d.setFixDuration(30);

        d = dft(DefectStatus.FIXED, engineer,
          "分页查询未返回总条数导致前端分页器错误",
          "缺陷列表API返回数据中total字段始终为null，前端分页器无法正确计算总页数",
          "分页器显示'共NaN页'，页码按钮无法点击",
          "生产环境",
          "1.进入缺陷列表\n2.查看底部分页器\n3.观察总页数显示",
          "分页器正确显示总页数和总条数",
          "分页器显示NaN",
          2, 2, 2, 5, 3, 2);
        fullTransition(d, DefectStatus.FIXED, "修复完成，已提交代码");
        d.setRootCauseHypothesis("DefectRepository.findAll(Specification, Pageable)返回的Page对象未正确映射totalElements");
        d.setFixPlan("在DefectService中将Page.getTotalElements()显式写入响应DTO");
        d.setFixContent("在DefectService.listDefects方法中添加response.setTotal(page.getTotalElements())");
        d.setAffectedModules("DefectService, DefectListResponse");
        d.setFixDuration(15);

        d = dft(DefectStatus.FIXED, engineer,
          "附件删除后磁盘文件未清理",
          "在系统中删除附件后，数据库记录删除了但uploads/目录下的物理文件未被删除，占用磁盘空间",
          "删除3条附件后，uploads/目录下对应的3个文件仍然存在",
          "生产环境",
          "1.上传一个附件\n2.在缺陷详情中删除该附件\n3.登录服务器检查uploads目录",
          "删除附件时同步删除数据库记录和物理文件",
          "物理文件未被删除",
          3, 2, 3, 3, 2, 2);
        fullTransition(d, DefectStatus.FIXED, "修复完成，已提交代码");
        d.setRootCauseHypothesis("AttachmentRepository.delete仅删除了数据库记录，未调用FileService删除物理文件");
        d.setFixPlan("在DefectService的删除附件方法中，先获取附件记录拿到文件路径，删除数据库记录后再删除物理文件");
        d.setFixContent("在deleteAttachment方法中添加: Files.deleteIfExists(Paths.get(attachment.getFilePath()))");
        d.setAffectedModules("DefectService, FileService");
        d.setFixDuration(20);

        d = dft(DefectStatus.FIXED, engineer,
          "特殊字符导致搜索SQL注入风险",
          "搜索框输入单引号'时，后端JPA查询报SQL语法错误，存在SQL注入隐患",
          "在搜索框输入'; DROP TABLE defects; --，后端返回500错误",
          "生产环境",
          "1.在搜索框输入单引号字符'\n2.点击搜索\n3.观察后端响应",
          "特殊字符被正确转义，不触发SQL错误",
          "后端返回500，日志显示SQL语法错误",
          4, 3, 5, 2, 1, 4);
        fullTransition(d, DefectStatus.FIXED, "修复完成，已提交代码");
        d.setRootCauseHypothesis("使用JPA Criteria API拼接Like条件时，未对用户输入做转义");
        d.setFixPlan("使用CriteriaBuilder.like时对特殊字符(%, _, ')做转义处理");
        d.setFixContent("添加escapeSpecialChars工具方法，对用户输入中的%, _, ', \\进行反斜杠转义");
        d.setAffectedModules("DefectRepository, DefectSpecification");
        d.setFixDuration(45);

        d = dft(DefectStatus.FIXED, engineer,
          "静态资源缓存策略缺失导致重复加载",
          "前端JS/CSS/图片等静态资源未设置Cache-Control头，用户每次刷新页面都需要重新下载全部资源",
          "刷新页面后，Network面板显示所有JS文件返回200而非304，传输数据量超过5MB",
          "生产环境，Nginx + Vite",
          "1.打开DevTools Network面板\n2.刷新页面\n3.观察JS/CSS文件的状态码和大小",
          "静态资源（带hash的chunk文件）返回304或使用强缓存，传输量<500KB",
          "全部资源重新下载，传输量>5MB",
          2, 3, 2, 5, 3, 2);
        fullTransition(d, DefectStatus.FIXED, "修复完成，已提交代码");
        d.setRootCauseHypothesis("Nginx配置中未对带hash的静态资源设置expires头，Vite打包的assets也未配置缓存策略");
        d.setFixPlan("在Nginx配置中对/assets/路径设置Cache-Control: public, max-age=31536000, immutable");
        d.setFixContent("添加Nginx location ~* /assets/.*\\.(js|css|png|jpg|svg|woff2)$ { expires 1y; add_header Cache-Control 'public, immutable'; }");
        d.setAffectedModules("Nginx配置");
        d.setFixDuration(15);

        d = dft(DefectStatus.FIXED, engineer,
          "列表行点击区域与复选框区域冲突",
          "缺陷列表中每行最左侧有复选框，但点击复选框时同时触发了行点击事件（打开Sheet），无法正常勾选",
          "点击第3行左侧的复选框，勾选后又立即打开了该缺陷的Sheet面板",
          "Chrome 125，全平台",
          "1.进入缺陷列表\n2.点击某行左侧的复选框\n3.观察是否同时触发了Sheet打开",
          "点击复选框只触发勾选，不触发行点击",
          "复选框勾选的同时Sheet面板被打开",
          2, 2, 1, 5, 3, 2);
        fullTransition(d, DefectStatus.FIXED, "修复完成，已提交代码");
        d.setRootCauseHypothesis("行点击事件未判断事件源是否为复选框，checkbox的click事件冒泡到了行元素");
        d.setFixPlan("在行点击事件处理函数中添加event.target.type === 'checkbox'判断，排除复选框点击");
        d.setFixContent("添加 if (e.target.type === 'checkbox') return; 在行点击处理函数开头");
        d.setAffectedModules("前端 DefectListTable组件");
        d.setFixDuration(10);

        d = dft(DefectStatus.FIXED, engineer,
          "快速切换Tab时组件状态异常",
          "在缺陷列表和知识库之间快速切换Tab，偶发出现页面内容重叠（两个Tab的内容同时显示）",
          "快速点击顶栏Tab在缺陷列表和知识库之间切换5次，页面出现内容重叠",
          "Chrome 125，生产环境",
          "1.快速连续点击顶栏'缺陷列表'和'知识库'Tab\n2.观察页面渲染",
          "Tab切换后只显示当前选中Tab的内容",
          "两个页面内容重叠显示",
          2, 2, 2, 3, 2, 2);
        fullTransition(d, DefectStatus.FIXED, "修复完成，已提交代码");
        d.setRootCauseHypothesis("TanStack Router的路由切换动画与React组件卸载时序冲突，旧组件在动画结束前未被卸载");
        d.setFixPlan("在Tab切换时取消进行中的路由过渡动画，确保旧路由组件立即卸载");
        d.setFixContent("使用router.navigate时设置resetScroll:false并确保replace为true，避免组件叠加");
        d.setAffectedModules("前端路由配置, __root.tsx");
        d.setFixDuration(60);

        d = dft(DefectStatus.FIXED, engineer,
          "某些情况下列表页筛选后总数为负数",
          "应用多个筛选条件后，列表页的统计栏'总缺陷数'显示为负数（如-3）",
          "筛选状态=CLOSED且优先级=P0，统计栏显示-3条",
          "生产环境",
          "1.进入缺陷列表\n2.设置筛选条件：状态=CLOSED，优先级=P0\n3.观察统计栏数字",
          "统计数字始终为非负整数",
          "显示负数",
          2, 2, 1, 2, 3, 2);
        fullTransition(d, DefectStatus.FIXED, "修复完成，已提交代码");
        d.setRootCauseHypothesis("前端统计数据使用了筛选前的总数减去筛选后的数量，当筛选后数量大于总数时出现负数");
        d.setFixPlan("统计数字直接从API返回的筛选结果中获取，不做前端二次计算");
        d.setFixContent("StatsBar组件直接使用defects.length和状态分布，移除多余的减法逻辑");
        d.setAffectedModules("前端 StatsBar组件");
        d.setFixDuration(20);

        d = dft(DefectStatus.FIXED, engineer,
          "日期选择器在移动端无法正常使用",
          "创建时间筛选的日期选择器在手机浏览器中弹出后，无法通过触摸滚动选择年份和月份",
          "iPhone 15 Pro Safari，点击日期选择器，月份列表无法触摸滚动",
          "iOS Safari / Android Chrome，屏幕宽度<768px",
          "1.用手机浏览器打开缺陷列表\n2.点击创建时间筛选\n3.尝试选择自定义日期范围",
          "日期选择器在移动端支持触摸滑动选择",
          "无法通过触摸操作选择日期",
          2, 3, 1, 3, 3, 2);
        fullTransition(d, DefectStatus.FIXED, "修复完成，已提交代码");
        d.setRootCauseHypothesis("使用的日期选择器组件库未适配移动端触摸事件");
        d.setFixPlan("替换为支持移动端的日期选择器或添加原生input[type=date]作为移动端降级方案");
        d.setFixContent("添加设备检测，移动端使用原生<input type='date'>替代自定义日期选择器");
        d.setAffectedModules("前端 日期筛选组件");
        d.setFixDuration(45);

        d = dft(DefectStatus.FIXED, engineer,
          "后端CORS配置过于宽松",
          "CORS配置中allowedOrigins设置为*，且同时allowCredentials=true，浏览器拒绝该配置且存在安全隐患",
          "Chrome控制台报错: 'The Access-Control-Allow-Origin header contains the value * when the credentials mode is include'",
          "全环境",
          "1.打开浏览器DevTools Console\n2.观察CORS相关错误\n3.检查响应头Access-Control-Allow-Origin",
          "CORS配置正确，allowedOrigins指定具体域名，与credentials兼容",
          "CORS配置冲突导致浏览器拒绝跨域请求",
          3, 2, 4, 5, 1, 3);
        fullTransition(d, DefectStatus.FIXED, "修复完成，已提交代码");
        d.setRootCauseHypothesis("WebConfig中同时设置了allowedOrigins(*).allowCredentials(true)，违反CORS规范");
        d.setFixPlan("将allowedOrigins改为具体的允许域名列表，如http://localhost:3001, https://生产域名");
        d.setFixContent("修改WebConfig: .allowedOrigins('http://localhost:3001','http://localhost:3000')");
        d.setAffectedModules("WebConfig.java");
        d.setFixDuration(10);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  VERIFIED — 8 条   (已验证)
    // ══════════════════════════════════════════════════════════════════════

    private void seedVerified() {
        Defect d;
        d = dft(DefectStatus.VERIFIED, qa,
          "MySQL连接未使用SSL导致数据传输明文",
          "后端服务与MySQL之间的数据库连接未启用SSL加密，SQL查询和数据在网络上明文传输",
          "网络抓包工具可捕获到明文的SQL查询语句和返回数据",
          "生产环境，MySQL 8.0",
          "1.使用Wireshark抓包\n2.过滤MySQL端口3307的流量\n3.检查数据包内容",
          "数据库连接启用SSL，抓包无法看到明文数据",
          "SQL语句和返回数据明文可读",
          4, 2, 5, 5, 1, 4);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("JDBC连接URL中useSSL=false且未配置SSL证书");
        d.setFixPlan("修改JDBC URL为useSSL=true，配置truststore和client证书路径");
        d.setFixContent("修改application.properties: jdbc:mysql://localhost:3307/defect_triage?useSSL=true&requireSSL=true&trustCertificateKeyStoreUrl=classpath:truststore.jks");
        d.setAffectedModules("application.properties, MySQL配置");
        d.setFixDuration(30);
        d.setVerificationResult("抓包验证无明文数据，数据库连接已加密");
        d.setRegressionScope("所有数据库读写操作");
        d.setVerificationConclusion("修复有效，数据库连接已启用SSL加密");

        d = dft(DefectStatus.VERIFIED, qa,
          "服务器重启后Token全部失效用户被强制登出",
          "后端服务重启后，所有已登录用户的JWT Token失效，需全部重新登录",
          "部署新版本重启后端，所有在线用户被强制跳转到登录页",
          "生产环境",
          "1.登录系统获取Token\n2.重启后端服务\n3.用原Token请求API",
          "服务重启后已有Token仍然有效",
          "Token失效返回401",
          2, 4, 3, 2, 3, 3);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("JWT签名密钥在每次启动时重新生成，导致旧Token签名验证失败");
        d.setFixPlan("将JWT密钥改为从配置文件/环境变量读取，确保重启前后一致");
        d.setFixContent("JwtUtil中密钥改为从app.jwt.secret配置读取（已配置但代码中使用了随机生成的密钥）");
        d.setAffectedModules("JwtUtil.java");
        d.setFixDuration(20);
        d.setVerificationResult("重启后端后原有Token仍可正常使用");
        d.setRegressionScope("所有API认证");
        d.setVerificationConclusion("修复有效，重启后Token保持有效");

        d = dft(DefectStatus.VERIFIED, qa,
          "前端错误边界未捕获异步错误导致白屏",
          "React组件在useEffect中异步请求失败且未被错误边界捕获，导致整个页面白屏",
          "后端API暂时不可用时，前端页面白屏而非显示错误提示",
          "Chrome 125，生产环境",
          "1.停止后端服务\n2.在前端页面操作\n3.观察页面反应",
          "API请求失败时显示友好的错误提示和重试按钮",
          "页面白屏",
          4, 4, 3, 5, 1, 4);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("React Error Boundary只能捕获渲染期间的错误，无法捕获异步回调中的错误");
        d.setFixPlan("在API client中添加全局错误处理，网络错误时显示全局错误提示而非白屏；添加Suspense fallback");
        d.setFixContent("在api/client.ts的fetch封装中添加catch处理，对网络错误返回统一错误对象并在组件中显示ErrorBanner");
        d.setAffectedModules("api/client.ts, 全局错误处理组件");
        d.setFixDuration(60);
        d.setVerificationResult("关闭后端服务后，前端显示'服务不可用'提示和重试按钮，不再白屏");
        d.setRegressionScope("所有页面和组件的错误处理");
        d.setVerificationConclusion("修复有效，异步错误不再导致白屏");

        d = dft(DefectStatus.VERIFIED, qa,
          "列表页数据缓存导致查看不到最新缺陷",
          "TanStack Query的staleTime设置过长（30分钟），用户新建缺陷后在列表中看不到",
          "创建一条新缺陷后返回列表页，新缺陷未出现，需要手动刷新",
          "全平台",
          "1.创建一条新缺陷\n2.返回缺陷列表\n3.观察列表是否包含新缺陷",
          "创建/编辑缺陷后列表自动刷新",
          "新缺陷不显示，需手动刷新",
          3, 4, 3, 5, 2, 3);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("TanStack Query的staleTime配置为30分钟，创建缺陷后未手动invalidate查询缓存");
        d.setFixPlan("在创建/编辑缺陷的mutation onSuccess回调中调用queryClient.invalidateQueries({queryKey:['defects']})");
        d.setFixContent("在useCreateDefect的onSuccess中添加queryClient.invalidateQueries(['defects'])");
        d.setAffectedModules("前端 API hooks");
        d.setFixDuration(15);
        d.setVerificationResult("创建缺陷后列表立即刷新显示新数据");
        d.setRegressionScope("所有缺陷列表查询");
        d.setVerificationConclusion("缓存失效策略正确，数据实时性达标");

        d = dft(DefectStatus.VERIFIED, qa,
          "用户输入的错误密码次数未限制（暴力破解风险）",
          "登录接口未限制密码错误次数，攻击者可暴力破解用户密码",
          "用Burp Suite对登录接口连续发送100次错误密码请求，全部返回200(登录失败但未锁定)",
          "生产环境",
          "1.对登录接口连续发送错误密码\n2.观察响应和账户状态",
          "连续5次错误密码后账户锁定15分钟",
          "无任何限制，可无限重试",
          5, 3, 5, 2, 1, 5);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("AuthService.login方法中未实现登录失败计数和锁定机制");
        d.setFixPlan("在Redis或数据库中记录每个账号的登录失败次数，连续5次失败后锁定15分钟");
        d.setFixContent("在AuthService.login中添加: 使用Guava RateLimiter+失败计数Map，5次失败后返回'账户已锁定，请15分钟后重试'");
        d.setAffectedModules("AuthService, User实体(lockUntil字段)");
        d.setFixDuration(90);
        d.setVerificationResult("连续5次错误密码后账户被锁定，第6次返回锁定提示");
        d.setRegressionScope("登录认证流程");
        d.setVerificationConclusion("修复有效，暴力破解防护已就位");

        d = dft(DefectStatus.VERIFIED, qa,
          "富文本编辑器的粘贴内容未过滤XSS",
          "缺陷描述字段使用富文本编辑器，粘贴HTML内容时未做XSS过滤，可执行恶意脚本",
          "在缺陷描述中粘贴<script>alert('xss')</script>，保存后打开缺陷详情，脚本被执行",
          "全平台",
          "1.创建缺陷\n2.在描述字段粘贴<script>alert('xss')</script>\n3.提交后打开该缺陷详情",
          "脚本被过滤或转义，不执行",
          "alert弹窗出现",
          5, 4, 5, 2, 1, 5);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("前端dangerouslySetInnerHTML直接渲染用户输入的HTML，后端也未做XSS过滤");
        d.setFixPlan("前端使用DOMPurify库过滤HTML内容，后端添加Jsoup过滤");
        d.setFixContent("前端: import DOMPurify from 'dompurify'; 渲染前调用DOMPurify.sanitize(content); 后端: Jsoup.clean(input, Safelist.basic())");
        d.setAffectedModules("前端 DefectDetailSheet, 后端 DefectService");
        d.setFixDuration(60);
        d.setVerificationResult("粘贴<script>等恶意代码后，被过滤为纯文本，不再执行");
        d.setRegressionScope("所有用户输入渲染场景");
        d.setVerificationConclusion("XSS防护有效，恶意脚本被正确过滤");

        d = dft(DefectStatus.VERIFIED, qa,
          "用户个人信息修改后页面未更新显示名称",
          "在个人设置中修改了显示名称，但顶栏的用户名仍然显示旧名称",
          "将显示名从'张三'改为'张三丰'，顶栏仍显示'张三'",
          "全平台",
          "1.进入个人设置\n2.修改显示名称\n3.保存\n4.观察顶栏用户名",
          "顶栏用户名立即更新为新名称",
          "顶栏仍显示旧名称",
          2, 2, 1, 2, 3, 2);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("修改用户信息后，AuthContext中的user对象未更新，依赖的useState未重新设置");
        d.setFixPlan("在修改用户信息的mutation onSuccess中调用authContext.refreshUser()更新全局用户状态");
        d.setFixContent("在PersonalSettings的保存成功回调中添加: await refreshUser()");
        d.setAffectedModules("AuthContext, PersonalSettings组件");
        d.setFixDuration(20);
        d.setVerificationResult("修改显示名称后顶栏立即更新");
        d.setRegressionScope("用户信息显示相关");
        d.setVerificationConclusion("修复有效，用户信息修改后全局同步");

        d = dft(DefectStatus.VERIFIED, qa,
          "后端接口未做请求频率限制导致被刷",
          "后端API未配置Rate Limiting，恶意用户可用脚本短时间内发起大量请求",
          "用脚本在10秒内发起1000次/api/defects请求，全部返回200",
          "生产环境，Spring Boot 3.4",
          "1.编写脚本循环请求/api/defects\n2.10秒内发起1000次请求\n3.观察响应状态码",
          "超过频率限制后返回429 Too Many Requests",
          "所有请求都返回200",
          4, 2, 4, 3, 1, 3);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("Spring Boot未引入任何Rate Limiter组件");
        d.setFixPlan("使用Bucket4j或Resilience4j实现令牌桶限流，每个用户每秒最多10次请求");
        d.setFixContent("添加RateLimitFilter拦截器，使用ConcurrentHashMap+AtomicInteger实现简易令牌桶，超限返回429");
        d.setAffectedModules("RateLimitFilter, WebConfig");
        d.setFixDuration(60);
        d.setVerificationResult("超过每秒10次限制后返回429状态码");
        d.setRegressionScope("所有API接口");
        d.setVerificationConclusion("限流机制生效，有效防止接口被刷");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CLOSED — 18 条   (已关闭 + 知识库沉淀)
    // ══════════════════════════════════════════════════════════════════════

    private void seedClosed() {
        Defect d;
        // --- 1 ---
        d = dft(DefectStatus.CLOSED, qa,
          "文件上传扩展名校验缺失",
          "附件上传未校验文件扩展名，可上传任意类型文件包括.exe等可执行文件",
          "上传.exe文件到附件区域，上传成功",
          "全环境",
          "1.点击上传附件\n2.选择一个.exe文件\n3.等待上传完成",
          "拒绝非白名单类型文件并提示",
          ".exe文件上传成功",
          5, 4, 5, 3, 1, 4);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("FileService未实现文件类型白名单校验");
        d.setFixPlan("在FileService.upload中添加MIME类型白名单校验");
        d.setFixContent("添加ALLOWED_TYPES Set，在upload方法中校验ContentType");
        d.setAffectedModules("FileService");
        d.setFixDuration(45);
        d.setVerificationResult(".exe文件被正确拒绝，正常文件上传不受影响");
        d.setRegressionScope("所有附件上传功能");
        d.setVerificationConclusion("修复完整，安全风险已消除");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "文件上传扩展名校验缺失");

        // --- 2 ---
        d = dft(DefectStatus.CLOSED, qa,
          "订单支付超时未回滚导致资金风险",
          "用户支付后支付网关超时，系统未执行回滚操作，订单状态停留在'支付中'且资金已扣",
          "大额订单支付接口超时(500ms)，页面卡死，后台订单状态不一致",
          "生产环境, Spring Boot 3.x",
          "1.提交大额订单\n2.模拟支付网关超时\n3.观察订单状态和资金变动",
          "超时后自动触发回滚，订单状态恢复为待支付，资金退回",
          "订单状态卡在支付中，资金已扣除",
          5, 5, 5, 2, 1, 5);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("支付超时异常被全局异常处理器捕获后只记录了日志，未触发补偿事务");
        d.setFixPlan("在支付超时catch块中添加补偿逻辑，调用支付网关撤销接口并回滚订单状态");
        d.setFixContent("添加@Transactional+补偿Saga模式：超时后异步调用refund，订单状态恢复为CANCELLED");
        d.setAffectedModules("PaymentService, OrderService");
        d.setFixDuration(180);
        d.setVerificationResult("模拟超时场景后订单正确回滚，资金退回");
        d.setRegressionScope("所有支付相关接口");
        d.setVerificationConclusion("交易安全，回滚机制完备");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "订单支付超时未回滚");

        // --- 3 ---
        d = dft(DefectStatus.CLOSED, qa,
          "API响应时间超过3秒(N+1查询问题)",
          "缺陷列表页面加载缓慢，API响应时间平均3.2秒，影响用户体验",
          "首页加载时API平均响应时间3.2秒，查看数据库日志发现大量单条查询",
          "生产环境",
          "1.访问首页\n2.打开DevTools Network面板\n3.观察/api/defects响应时间",
          "API响应时间<1秒",
          "平均3.2秒",
          4, 4, 4, 5, 1, 3);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("N+1查询问题，Defect列表查询时对每个Defect的reporter/assignee/verifier分别查一次");
        d.setFixPlan("添加@EntityGraph注解批量加载关联数据");
        d.setFixContent("在DefectRepository.findAll方法添加@EntityGraph(attributePaths={'reporter','assignee','verifier'})");
        d.setAffectedModules("DefectRepository, DefectService");
        d.setFixDuration(120);
        d.setVerificationResult("API响应时间降至0.8秒");
        d.setRegressionScope("所有缺陷列表和详情查询");
        d.setVerificationConclusion("性能优化显著，响应时间达标");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "API响应时间优化");

        // --- 4 ---
        d = dft(DefectStatus.CLOSED, qa,
          "搜索功能对中文分词支持异常",
          "搜索框输入中文关键词无搜索结果，但缺陷标题中包含该关键词",
          "输入'登录'搜索，返回0条结果，但存在标题为'登录页面样式错乱'的缺陷",
          "全平台",
          "1.在搜索框输入中文关键词\n2.点击搜索\n3.观察搜索结果",
          "返回标题或描述包含关键词的所有缺陷",
          "返回空列表",
          3, 3, 3, 5, 2, 2);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("MySQL默认LIKE查询对中文分词效果差，且未建立全文索引");
        d.setFixPlan("在缺陷标题和描述字段添加MySQL FULLTEXT索引，使用MATCH...AGAINST查询");
        d.setFixContent("添加FULLTEXT INDEX idx_ft_title_desc ON defects(title, description); 查询改用MATCH...AGAINST IN BOOLEAN MODE");
        d.setAffectedModules("数据库索引, DefectRepository");
        d.setFixDuration(60);
        d.setVerificationResult("搜索'登录'返回所有相关缺陷");
        d.setRegressionScope("搜索功能");
        d.setVerificationConclusion("中文搜索召回率达标");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "中文搜索优化");

        // --- 5 ---
        d = dft(DefectStatus.CLOSED, qa,
          "列表分页跳转异常",
          "在缺陷列表点击第3页分页按钮，页面跳转回第1页",
          "缺陷列表共55页，点击第3页后URL参数变为page=3但瞬间又跳回page=1",
          "Chrome/Safari",
          "1.进入缺陷列表\n2.点击第3页分页按钮",
          "跳转至第3页",
          "跳转回第1页",
          2, 2, 2, 5, 5, 1);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("分页参数page在URL解析时被默认为1覆盖");
        d.setFixPlan("修复URL search params解析逻辑，确保page参数正确读取");
        d.setFixContent("修改router.tsx中search params的parse逻辑: page: z.number().default(1).catch(1)");
        d.setAffectedModules("前端路由模块");
        d.setFixDuration(60);
        d.setVerificationResult("分页跳转正常");
        d.setRegressionScope("所有分页功能");
        d.setVerificationConclusion("分页功能正常");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "列表分页跳转修复");

        // --- 6 ---
        d = dft(DefectStatus.CLOSED, qa,
          "Excel导出内存溢出",
          "导出超过10万条缺陷数据时，服务器OOM崩溃",
          "查询10万条记录点击导出Excel，服务器Java进程内存飙升后崩溃",
          "生产环境, JDK 21",
          "1.确保数据库有10万条缺陷\n2.点击导出全部\n3.观察服务器状态",
          "正常导出Excel文件，内存使用平稳",
          "服务OOM崩溃重启",
          5, 5, 5, 2, 1, 4);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("一次性将10万条数据加载到内存List中，再写入Excel，导致堆内存耗尽");
        d.setFixPlan("改为流式写入SXSSFWorkbook，分批从数据库读取，每批1000条写入后释放");
        d.setFixContent("使用Apache POI SXSSFWorkbook替代XSSFWorkbook，结合JPA Stream查询分批处理");
        d.setAffectedModules("ExportService, DefectRepository");
        d.setFixDuration(120);
        d.setVerificationResult("导出10万条数据内存使用稳定在500MB以内");
        d.setRegressionScope("所有导出功能");
        d.setVerificationConclusion("内存使用可控，导出功能稳定");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "Excel导出OOM优化");

        // --- 7 ---
        d = dft(DefectStatus.CLOSED, qa,
          "通知消息延迟严重（超过30分钟）",
          "用户触发操作后，通知消息延迟30分钟以上才收到",
          "触发缺陷流转通知，30分钟后才收到站内信",
          "生产环境",
          "1.执行缺陷流转操作\n2.观察通知中心",
          "3秒内收到通知",
          "延迟超过30分钟",
          4, 4, 4, 4, 3, 3);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("消息队列消费者线程池耗尽，消息堆积在队列中");
        d.setFixPlan("增加消费者线程数+引入消息优先级+添加队列堆积告警");
        d.setFixContent("消费者线程池从core=2/max=4调整为core=8/max=16；添加队列深度>1000的Grafana告警");
        d.setAffectedModules("NotificationConsumer, 线程池配置");
        d.setFixDuration(90);
        d.setVerificationResult("通知延迟降至2秒以内");
        d.setRegressionScope("所有通知消息");
        d.setVerificationConclusion("消息延迟达标");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "通知消息延迟优化");

        // --- 8 ---
        d = dft(DefectStatus.CLOSED, qa,
          "用户会话在多个Tab间不同步",
          "在一个浏览器Tab中登录，另一个Tab中仍然显示未登录状态",
          "Tab A登录成功，切换到Tab B刷新后仍显示登录页",
          "Chrome 125 / Firefox 127",
          "1.在Tab A打开系统并登录\n2.在Tab B打开系统\n3.观察Tab B的登录状态",
          "所有Tab共享登录状态",
          "Tab B仍显示未登录",
          2, 3, 2, 3, 3, 2);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("JWT Token存储在localStorage，不同Tab间localStorage不同步触发React状态更新");
        d.setFixPlan("监听storage事件，当其他Tab修改localStorage时同步更新AuthContext");
        d.setFixContent("在AuthContext中添加window.addEventListener('storage', handler)，检测token变化并同步状态");
        d.setAffectedModules("AuthContext.tsx");
        d.setFixDuration(30);
        d.setVerificationResult("Tab A登录后，Tab B自动刷新为已登录状态");
        d.setRegressionScope("登录状态管理");
        d.setVerificationConclusion("多Tab会话同步正常");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "多Tab会话同步");

        // --- 9 ---
        d = dft(DefectStatus.CLOSED, qa,
          "用户注册时邮箱验证码发送频率未限制",
          "注册接口的邮箱验证码发送未做频率限制，可被恶意利用向任意邮箱频繁发送",
          "连续点击'发送验证码'按钮10次，每次都成功发送",
          "生产环境",
          "1.进入注册页\n2.输入邮箱地址\n3.快速连续点击发送验证码\n4.检查邮箱收件",
          "同一邮箱60秒内只能发送1次验证码",
          "10秒内收到10封验证码邮件",
          4, 3, 5, 2, 1, 4);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("验证码发送接口未做频率限制和防刷校验");
        d.setFixPlan("在发送验证码接口添加Redis缓存记录，60秒内相同邮箱/ip不允许重复发送");
        d.setFixContent("在AuthService.sendVerificationCode中添加redisTemplate.opsForValue().setIfAbsent('vc:'+email, '1', 60, SECONDS)");
        d.setAffectedModules("AuthService, Redis配置");
        d.setFixDuration(45);
        d.setVerificationResult("60秒内重复请求返回'请勿频繁发送验证码'");
        d.setRegressionScope("所有验证码发送场景");
        d.setVerificationConclusion("频率限制生效");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "验证码发送频率限制");

        // --- 10 ---
        d = dft(DefectStatus.CLOSED, qa,
          "缺陷详情页面的'修改记录'和'流转历史'重复",
          "缺陷详情Sheet中有两个Tab分别叫'修改记录'和'流转历史'，两者展示的内容几乎相同",
          "查看缺陷#42的修改记录和流转历史Tab，内容90%重叠",
          "全平台",
          "1.打开缺陷详情Sheet\n2.查看'修改记录'Tab\n3.查看'流转历史'Tab\n4.对比内容",
          "两个Tab有明确区分：流转历史=状态变更，修改记录=字段级别编辑",
          "两个Tab内容高度重复",
          1, 2, 1, 4, 3, 2);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("前端将StateTransition数据和字段变更数据混在一起展示");
        d.setFixPlan("拆分数据源：流转历史显示状态变更，修改记录显示字段编辑(需后端支持字段级审计日志)");
        d.setFixContent("修改记录Tab暂时合并到流转历史，统一显示时间线，每条记录标注类型(状态变更/字段编辑)");
        d.setAffectedModules("前端 DefectDetailSheet");
        d.setFixDuration(30);
        d.setVerificationResult("两个Tab合并为统一时间线，不再重复");
        d.setRegressionScope("缺陷详情Sheet");
        d.setVerificationConclusion("信息展示清晰不再冗余");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "修改记录与流转历史合并");

        // --- 11 ---
        d = dft(DefectStatus.CLOSED, qa,
          "第三方OAuth登录回调地址配置错误",
          "配置了GitHub OAuth登录，但点击GitHub登录后回调到localhost:3001而非实际域名",
          "生产环境GitHub OAuth回调地址仍为localhost，导致回调失败",
          "生产环境",
          "1.点击GitHub登录按钮\n2.完成GitHub授权\n3.观察回调URL",
          "回调到正确的生产域名",
          "回调到localhost:3001导致404",
          4, 4, 3, 3, 3, 3);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("OAuth回调地址硬编码在前端环境变量中，生产部署时未修改");
        d.setFixPlan("回调地址改为从后端API动态获取，后端根据请求Host自动拼接");
        d.setFixContent("添加GET /api/auth/oauth-redirect-url接口，根据request.getHeader('Host')动态返回回调地址");
        d.setAffectedModules("AuthController, 前端OAuth配置");
        d.setFixDuration(45);
        d.setVerificationResult("回调地址正确跳转到生产域名");
        d.setRegressionScope("OAuth登录流程");
        d.setVerificationConclusion("OAuth回调地址根据环境自适应");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "OAuth回调地址修复");

        // --- 12 ---
        d = dft(DefectStatus.CLOSED, qa,
          "批量操作缺少进度提示",
          "对100条缺陷执行批量操作时，页面无任何进度提示，用户不知道是否在处理中",
          "选中100条缺陷->批量修改优先级为P2->点击确认后页面无反应约5秒",
          "生产环境",
          "1.选中100条缺陷\n2.点击批量修改优先级\n3.选择P2\n4.点击确认",
          "显示进度条或处理中的loading状态",
          "无任何反应约5秒后突然完成",
          2, 3, 2, 3, 3, 3);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("前端批量操作未设置loading状态，后端未返回处理进度");
        d.setFixPlan("前端按钮显示处理中状态，后端批量操作改为异步并返回任务ID供前端轮询进度");
        d.setFixContent("批量操作按钮添加loading+进度百分比；后端使用@Async处理批量任务，前端每2秒轮询进度");
        d.setAffectedModules("前端批量操作组件, 后端BatchService");
        d.setFixDuration(90);
        d.setVerificationResult("批量操作时显示'正在处理 (45/100)...'进度提示");
        d.setRegressionScope("所有批量操作");
        d.setVerificationConclusion("进度提示提升用户体验");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "批量操作进度提示");

        // --- 13 ---
        d = dft(DefectStatus.CLOSED, qa,
          "Spring Boot优雅关闭未配置导致请求中断",
          "K8s滚动更新时，旧Pod直接终止导致处理中的请求被中断",
          "部署更新时，正在处理的流转请求返回502 Bad Gateway",
          "生产环境，K8s + Spring Boot 3.4",
          "1.触发一条流转操作\n2.在请求处理期间触发K8s滚动更新\n3.观察请求结果",
          "正在处理的请求完成后Pod才退出",
          "请求被中断返回502",
          3, 3, 4, 2, 1, 4);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("Spring Boot未配置graceful shutdown，K8s发送SIGTERM后立即终止进程");
        d.setFixPlan("配置Spring Boot graceful shutdown + K8s terminationGracePeriodSeconds");
        d.setFixContent("application.properties: server.shutdown=graceful; spring.lifecycle.timeout-per-shutdown-phase=30s; K8s: terminationGracePeriodSeconds=45");
        d.setAffectedModules("application.properties, K8s Deployment");
        d.setFixDuration(20);
        d.setVerificationResult("滚动更新时处理中的请求正常完成");
        d.setRegressionScope("部署流程");
        d.setVerificationConclusion("优雅关闭配置正确");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "优雅关闭配置");

        // --- 14 ---
        d = dft(DefectStatus.CLOSED, qa,
          "空状态页面引导不友好",
          "新用户登录后知识库为空时，页面显示'暂无数据'三个字，无任何引导操作",
          "新用户登录后进入知识库页面，只有'暂无数据'文字",
          "全平台",
          "1.用全新账号登录\n2.进入知识库页面\n3.观察页面内容",
          "空状态有友好的插图和引导文案，如'还没有知识条目，缺陷关闭后会自动生成'",
          "只有'暂无数据'三个字",
          1, 2, 1, 4, 4, 2);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("空状态组件仅显示文字，缺少插图和引导信息");
        d.setFixPlan("设计空状态组件：添加Lottie动画插图+引导文案+快捷操作按钮");
        d.setFixContent("创建EmptyState组件，根据不同页面显示不同的插图和引导文案，如知识库空状态显示'缺陷关闭后将自动生成知识条目'");
        d.setAffectedModules("前端 EmptyState组件, 知识库页面");
        d.setFixDuration(60);
        d.setVerificationResult("空状态页面有插图和引导文案");
        d.setRegressionScope("所有列表页空状态");
        d.setVerificationConclusion("用户体验提升");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "空状态引导优化");

        // --- 15 ---
        d = dft(DefectStatus.CLOSED, qa,
          "页面滚动位置在路由切换后未恢复",
          "在缺陷列表翻到第5页，点击打开某条缺陷Sheet，关闭Sheet后页面回到了第1页顶部",
          "浏览到列表中间位置，打开Sheet再关闭，列表回到了顶部",
          "全平台",
          "1.浏览缺陷列表到第3页\n2.点击某条缺陷打开Sheet\n3.关闭Sheet\n4.观察列表位置",
          "关闭Sheet后列表回到原来的滚动位置",
          "列表滚动到顶部",
          1, 2, 1, 4, 3, 2);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("Sheet关闭时触发了列表的重新渲染或scrollTop重置");
        d.setFixPlan("在Sheet打开前保存scrollTop，关闭后恢复；或使用TanStack Router的scrollRestoration");
        d.setFixContent("DefectList组件使用useRef保存scrollTop，Sheet关闭后在useEffect中恢复");
        d.setAffectedModules("前端 DefectList, Sheet组件");
        d.setFixDuration(30);
        d.setVerificationResult("关闭Sheet后列表滚动位置保持");
        d.setRegressionScope("所有列表滚动位置");
        d.setVerificationConclusion("滚动位置恢复正常");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "列表滚动位置恢复");

        // --- 16 ---
        d = dft(DefectStatus.CLOSED, qa,
          "用户账号注销后数据未做匿名化处理",
          "用户申请注销账号后，该用户创建的缺陷中reporter字段直接显示为null，而非'已注销用户'",
          "注销submitter账号后，该用户提交的缺陷reporter显示为null的空白头像",
          "生产环境",
          "1.用submitter账号创建一条缺陷\n2.管理员注销该账号\n3.查看该缺陷的提交人信息",
          "reporter显示为'已注销用户'，保留匿名标识",
          "reporter显示为null/空白",
          2, 2, 3, 1, 2, 2);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("用户注销时直接删除了User记录，关联缺陷的reporter_id外键变为null");
        d.setFixPlan("用户注销改为软删除+匿名化，保留记录但清除个人信息");
        d.setFixContent("User添加deleted字段，注销时软删除并匿名化displayName；Defect的reporter显示为'已注销用户'");
        d.setAffectedModules("User实体, AuthService, UserService");
        d.setFixDuration(60);
        d.setVerificationResult("注销后reporter显示为'已注销用户'");
        d.setRegressionScope("用户注销流程");
        d.setVerificationConclusion("数据匿名化处理正确");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "用户注销数据匿名化");

        // --- 17 ---
        d = dft(DefectStatus.CLOSED, qa,
          "表格排序后序号列乱序",
          "缺陷列表的序号列（第1列#号）在排序后随机排列而非保持1,2,3...顺序",
          "按优先级降序排列后，序号列显示为5,12,3,27...而不是1,2,3,4...",
          "全平台",
          "1.进入缺陷列表\n2.点击任意列头排序\n3.观察第1列序号",
          "序号始终为1,2,3,4...按当前页顺序排列",
          "序号随排序条件打乱",
          1, 2, 1, 5, 3, 2);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("序号使用了defect.id而非行索引row.index");
        d.setFixPlan("序号列使用row.index + page.offset + 1计算显示序号");
        d.setFixContent("改为 {row.index + 1} 或带分页的 {pageIndex * pageSize + row.index + 1}");
        d.setAffectedModules("前端 DefectListTable");
        d.setFixDuration(10);
        d.setVerificationResult("排序后序号保持1,2,3...顺序");
        d.setRegressionScope("所有表格序号列");
        d.setVerificationConclusion("序号列逻辑正确");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "表格序号列排序修复");

        // --- 18 ---
        d = dft(DefectStatus.CLOSED, qa,
          "创建缺陷时模态框ESC键关闭后表单数据丢失",
          "用户在创建缺陷弹窗中填写了大量内容，误按ESC键关闭弹窗后所有填写的内容丢失",
          "填写了缺陷的6个必填字段后误按ESC键，弹窗关闭，重新打开后表单为空",
          "全平台",
          "1.点击创建缺陷\n2.填写所有必填字段\n3.按ESC键\n4.重新打开创建弹窗",
          "ESC关闭弹窗前弹出确认提示'内容未保存，确定关闭？'或表单内容自动缓存",
          "弹窗直接关闭，所有填写内容丢失",
          3, 4, 2, 4, 2, 3);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("弹窗组件的onEscapeKeyDown直接关闭弹窗，未做未保存内容检测");
        d.setFixPlan("在ESC和遮罩点击关闭前检测表单dirty状态，有未保存内容时弹出二次确认");
        d.setFixContent("添加handleClose检测：if(formState.isDirty) { setShowConfirm(true) } else { close() }");
        d.setAffectedModules("前端 CreateDefectDialog");
        d.setFixDuration(30);
        d.setVerificationResult("填写内容后按ESC弹窗提示'内容未保存'");
        d.setRegressionScope("所有表单弹窗");
        d.setVerificationConclusion("表单内容保护机制有效");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "表单未保存内容保护");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  REOPENED — 2 条   (已重新打开)
    // ══════════════════════════════════════════════════════════════════════

    private void seedReopened() {
        Defect d;
        d = dft(DefectStatus.REOPENED, engineer,
          "修复后验证时发现原问题在特定条件下仍然复现",
          "缺陷修复后QA验证时发现：Chrome无痕模式下登录后，文件上传扩展名校验依然失效",
          "在Chrome无痕模式下，上传.exe文件仍然成功",
          "Chrome 125无痕模式",
          "1.打开Chrome无痕窗口\n2.登录系统\n3.上传.exe文件",
          "所有模式下.exe文件均被拒绝",
          "无痕模式下.exe文件上传成功",
          4, 3, 4, 2, 1, 3);
        fullTransition(d, DefectStatus.REOPENED, "验证不通过，重新打开");
        d.setRootCauseHypothesis("初次修复只在前端做了校验，无痕模式下Service Worker缓存未更新，仍有旧代码");
        d.setFixPlan("在后端FileService中也添加文件类型校验作为最终防线");
        d.setFixContent("在后端upload方法中添加ContentType白名单校验，前后端双重防护");
        d.setAffectedModules("FileService (后端)");

        d = dft(DefectStatus.REOPENED, engineer,
          "消息队列消费者重启后未处理积压消息",
          "之前修复的消息延迟问题，在MQ消费者重启后积压的消息被直接丢弃而非重新消费",
          "Kafka消费者重启后，重启期间的500条积压消息未被消费",
          "生产环境，Kafka",
          "1.向消息队列发送100条通知\n2.重启消费者服务\n3.检查消息消费情况",
          "重启后自动消费所有积压消息",
          "重启期间产生的消息被丢弃",
          4, 4, 4, 2, 2, 4);
        fullTransition(d, DefectStatus.REOPENED, "验证不通过，重新打开");
        d.setRootCauseHypothesis("Kafka消费者未配置auto.offset.reset=earliest，重启后从latest开始消费");
        d.setFixPlan("修改消费者配置auto.offset.reset=earliest，确保重启后不丢消息");
        d.setFixContent("Kafka consumer配置: enable.auto.commit=false, auto.offset.reset=earliest");
        d.setAffectedModules("Kafka Consumer配置");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EXTRA — ~100 条各类状态补充，总计达 ~200 条
    // ══════════════════════════════════════════════════════════════════════

    private void seedExtra() {
        Defect d;

        // ── DRAFT +5 ────────────────────────────────────────────────────
        d = dft(DefectStatus.DRAFT, null,
          "导入CSV时日期格式自动识别错误", "批量导入缺陷时日期格式dd/MM/yyyy无法识别报错",
          "导入CSV文件，日期列格式为31/12/2025，系统报Date parse error",
          "Chrome 125, Win11", "1.准备含dd/MM/yyyy日期格式的CSV\n2.点击导入\n3.选择文件", "日期正确解析为2025-12-31", "报错Date parse error，导入失败", 3,3,2,4,2,2);
        transition(d, DefectStatus.DRAFT, DefectStatus.DRAFT, submitter, "创建草稿");
        d = dft(DefectStatus.DRAFT, null,
          "缺陷指派后负责人未收到站内通知", "管理员将缺陷指派给工程师后，工程师通知中心无新消息",
          "管理员将缺陷#55指派给李四，李四登录后通知中心无任何提醒", "全平台",
          "1.管理员打开缺陷#55\n2.修改负责人为李四\n3.用李四账号登录\n4.查看通知中心", "李四收到'缺陷#55已分配给你'通知", "通知中心无消息", 2,3,1,3,3,2);
        transition(d, DefectStatus.DRAFT, DefectStatus.DRAFT, submitter, "创建草稿");
        d = dft(DefectStatus.DRAFT, null,
          "导出Excel时数字格式自动变成科学计数法", "缺陷ID和统计数据导出Excel后显示为科学计数法",
          "导出缺陷列表Excel，ID列123456显示为1.23E+05", "Excel 365, Win11",
          "1.导出缺陷列表Excel\n2.打开查看ID列", "ID正常显示数字", "显示科学计数法", 1,2,1,3,4,2);
        transition(d, DefectStatus.DRAFT, DefectStatus.DRAFT, submitter, "创建草稿");
        d = dft(DefectStatus.DRAFT, null,
          "通知中心消息无法标记全部已读", "通知中心有50条未读，只能逐条点击标记已读，无全部已读按钮",
          "通知中心累积50条消息，想批量标记已读但找不到按钮", "全平台",
          "1.进入通知中心\n2.查找全部标记已读按钮", "提供全部已读按钮", "只能逐条操作", 1,2,1,4,3,2);
        transition(d, DefectStatus.DRAFT, DefectStatus.DRAFT, submitter, "创建草稿");
        d = dft(DefectStatus.DRAFT, null,
          "页面字体大小切换后表格行高异常", "浏览器缩放或系统字体调整后表格行高过大",
          "系统字体设为150%后，表格行高变成正常2倍", "Win11辅助功能",
          "1.系统设置字体150%\n2.打开缺陷列表", "表格自适应字体变化", "行高异常", 1,2,1,2,3,2);
        transition(d, DefectStatus.DRAFT, DefectStatus.DRAFT, submitter, "创建草稿");

        // ── REPORTED +8 ─────────────────────────────────────────────────
        d = dft(DefectStatus.REPORTED, null,
          "服务健康检查端点偶发超时", "K8s存活探针调用/actuator/health偶发超时触发Pod重启",
          "Pod每2小时重启一次，日志显示/actuator/health返回耗时>5s", "生产环境 K8s",
          "1.观察Pod重启频率\n2.查看健康检查日志", "健康检查在1s内响应", "超时触发重启", 5,4,4,2,1,4);
        fullTransition(d, DefectStatus.REPORTED, "已提交分诊");
        d = dft(DefectStatus.REPORTED, null,
          "Docker镜像体积过大导致部署缓慢", "Docker镜像超过800MB，CI/CD拉取和部署耗时超10分钟",
          "docker push耗时5分钟，k8s拉取镜像又耗时5分钟", "CI/CD流水线",
          "1.构建Docker镜像\n2.docker images查看大小", "镜像<300MB", "镜像850MB", 2,2,1,3,3,2);
        fullTransition(d, DefectStatus.REPORTED, "已提交分诊");
        d = dft(DefectStatus.REPORTED, null,
          "用户个人设置页头像上传后不显示预览", "上传头像后页面无预览刷新，需手动刷新才能看到新头像",
          "上传新头像照片，页面仍显示默认头像", "Chrome 125",
          "1.进入个人设置\n2.上传头像\n3.观察头像变化", "上传后头像即时更新", "头像不变", 1,2,1,3,4,2);
        fullTransition(d, DefectStatus.REPORTED, "已提交分诊");
        d = dft(DefectStatus.REPORTED, null,
          "页面Ctrl+F浏览器搜索无法搜索折叠内容", "浏览器Ctrl+F搜索无法找到折叠在Sheet面板内的缺陷描述内容",
          "缺陷列表中使用Ctrl+F搜索某个缺陷标题，找不到因为它在当前页未展开", "Chrome 125",
          "1.打开缺陷列表\n2.Ctrl+F搜索关键词", "能搜到所有可见和已加载内容", "折叠内容搜不到", 2,3,1,4,2,2);
        fullTransition(d, DefectStatus.REPORTED, "已提交分诊");
        d = dft(DefectStatus.REPORTED, null,
          "长时间不操作后页面点击无响应", "浏览器Tab后台放置30分钟后切回，页面所有按钮点击无响应需刷新",
          "最小化浏览器30分钟后切回，列表页无法滚动无法点击", "Chrome 125",
          "1.打开系统\n2.切到其他Tab等待30分钟\n3.切回", "页面正常响应操作", "页面冻结需刷新", 3,4,2,5,3,2);
        fullTransition(d, DefectStatus.REPORTED, "已提交分诊");
        d = dft(DefectStatus.REPORTED, null,
          "移动端点击缺陷行触发两次事件", "手机浏览器中点击缺陷列表某行，同时触发了勾选和Sheet打开",
          "iPhone Safari点击列表行，先勾选了复选框又打开了Sheet", "iOS Safari",
          "1.手机浏览器打开缺陷列表\n2.点击某行", "仅打开Sheet不勾选", "同时勾选和打开Sheet", 2,3,1,5,3,2);
        fullTransition(d, DefectStatus.REPORTED, "已提交分诊");
        d = dft(DefectStatus.REPORTED, null,
          "列表筛选条件不能多选组合", "状态筛选只支持单选，不能同时查看多个状态",
          "想同时查看TRIAGING和ANALYZED的缺陷需要切换两次", "全平台",
          "1.点击状态筛选\n2.尝试多选", "支持多选状态筛选", "只能单选", 2,3,2,4,3,2);
        fullTransition(d, DefectStatus.REPORTED, "已提交分诊");
        d = dft(DefectStatus.REPORTED, null,
          "附件拖拽上传偶尔不触发", "将文件拖入上传区域约30%概率无反应需重新拖拽",
          "拖拽PNG截图到上传区偶尔无响应", "Chrome/Edge Win11",
          "1.拖拽文件到上传区\n2.重复10次", "每次拖拽都触发上传", "3次无反应", 2,3,1,3,4,2);
        fullTransition(d, DefectStatus.REPORTED, "已提交分诊");

        // ── TRIAGING +8 ─────────────────────────────────────────────────
        d = dft(DefectStatus.TRIAGING, engineer,
          "Docker Compose启动顺序导致应用连接MySQL失败", "docker-compose up时应用先于MySQL就绪启动导致连接失败",
          "docker-compose up后应用报Connection refused", "Docker Compose",
          "1.docker-compose down\n2.docker-compose up", "应用等待MySQL就绪后启动", "应用启动失败", 4,4,4,3,1,3);
        fullTransition(d, DefectStatus.TRIAGING, "分诊评估中");
        d = dft(DefectStatus.TRIAGING, engineer,
          "同一个浏览器多个账号快速切换时缓存混乱", "退出A账号登录B账号后列表仍显示A的数据",
          "A退出→B登录→列表仍显示A创建的缺陷", "Chrome 125",
          "1.A登录查看列表\n2.A退出\n3.B登录", "显示B的数据", "显示A的缓存数据", 3,3,2,3,2,3);
        fullTransition(d, DefectStatus.TRIAGING, "分诊评估中");
        d = dft(DefectStatus.TRIAGING, engineer,
          "Grafana监控面板缺少JVM线程数指标", "现有Grafana面板缺少JVM活跃线程数监控无法提前发现线程泄漏",
          "查看Grafana面板找不到thread相关指标", "Grafana+Prometheus",
          "1.打开Grafana\n2.搜索thread指标", "有jvm_threads_live等指标", "无线程相关指标", 2,2,2,4,3,2);
        fullTransition(d, DefectStatus.TRIAGING, "分诊评估中");
        d = dft(DefectStatus.TRIAGING, engineer,
          "前端bundle大小超过2MB首屏加载慢", "Vite打包后主bundle超过2MB，3G网络下首屏加载>6s",
          "Lighthouse报告提示Avoid large JavaScript payloads", "3G网络",
          "1.npm run build\n2.查看dist大小\n3.Lighthouse测试", "主bundle<500KB", "主bundle 2.3MB", 3,4,3,3,2,3);
        fullTransition(d, DefectStatus.TRIAGING, "分诊评估中");
        d = dft(DefectStatus.TRIAGING, engineer,
          "知识库条目内容中的图片链接失效", "知识条目引用外部图片链接已失效显示裂图",
          "排查手册中引用的架构图URL返回404", "生产环境",
          "1.打开知识条目详情\n2.查看图片", "图片正常显示或使用本地缓存", "显示裂图", 2,2,1,3,3,2);
        fullTransition(d, DefectStatus.TRIAGING, "分诊评估中");
        d = dft(DefectStatus.TRIAGING, engineer,
          "列表页筛选条件URL参数刷新后丢失", "设置筛选条件后刷新页面URL参数被清空",
          "筛选状态=TRIAGING&keyword=登录，F5刷新后回到无筛选状态", "Chrome 125",
          "1.设置筛选条件\n2.复制URL\n3.F5刷新", "筛选条件保持", "筛选条件丢失", 2,3,2,4,3,2);
        fullTransition(d, DefectStatus.TRIAGING, "分诊评估中");
        d = dft(DefectStatus.TRIAGING, engineer,
          "Redis缓存Key设计不规范导致难以排查", "缓存Key命名混乱无统一规范导致运维排查Key占用困难",
          "缓存Key有defect:123也有123:defect格式混乱", "生产环境",
          "1.redis-cli keys *\n2.查看Key命名", "Key统一如defect:cache:{id}", "命名混乱", 1,2,1,3,3,2);
        fullTransition(d, DefectStatus.TRIAGING, "分诊评估中");
        d = dft(DefectStatus.TRIAGING, engineer,
          "i18n国际化缺失导致英文用户看到中文占位符", "部分placeholder和tooltip未国际化英文用户看到中文",
          "英文语言设置下知识库搜索框placeholder显示中文'搜索知识条目'", "全平台英文环境",
          "1.切换语言为English\n2.查看各页面", "所有文案显示英文", "部分显示中文", 2,3,1,3,3,2);
        fullTransition(d, DefectStatus.TRIAGING, "分诊评估中");

        // ── ANALYZED +10 ─────────────────────────────────────────────────
        d = dft(DefectStatus.ANALYZED, engineer,
          "JPA懒加载在Controller层触发懒加载异常", "在Controller返回JSON时访问懒加载属性导致LazyInitializationException",
          "DefectController返回详情时访问verifier.getName()抛出异常", "生产环境",
          "1.请求/api/defects/1\n2.查看响应", "正确序列化关联对象", "抛出500异常", 4,3,3,3,1,3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("JPA Entity在事务外访问懒加载属性，OpenEntityManagerInViewFilter未配置");
        d = dft(DefectStatus.ANALYZED, engineer,
          "用户导出的CSV用WPS打开乱码", "用WPS打开导出的CSV文件中文全部乱码但Excel正常",
          "WPS打开缺陷导出CSV中文乱码", "WPS 2024",
          "1.导出CSV\n2.WPS打开", "中文正常显示", "中文乱码", 2,3,2,4,2,2);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("CSV文件未添加UTF-8 BOM头WPS默认用GBK解码");
        d = dft(DefectStatus.ANALYZED, engineer,
          "缺陷详情页大文本渲染导致滚动卡顿", "缺陷描述超5000字时Sheet面板滚动帧率<20fps",
          "打开描述含8000字缺陷详情Sheet滚动卡顿", "Chrome 125",
          "1.创建描述8000字缺陷\n2.打开Sheet\n3.滚动", "滚动流畅60fps", "卡顿<20fps", 2,3,2,3,3,2);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("大文本未虚拟化直接渲染到DOM导致大量DOM节点");
        d = dft(DefectStatus.ANALYZED, engineer,
          "系统缺少操作审计日志", "除流转记录外其余操作如编辑/删除/导出均无审计日志无法溯源",
          "安全审计要求所有写操作有日志但当前仅记录流转", "全环境",
          "1.编辑缺陷字段\n2.查看日志系统", "所有写操作有审计记录", "仅状态变更有记录", 3,2,4,5,1,3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("AuditLog切面未配置或只拦截了transition接口");
        d = dft(DefectStatus.ANALYZED, engineer,
          "导出功能在移动端浏览器无法使用", "移动端浏览器点击导出按钮无反应",
          "iPhone Safari点击导出无下载弹窗", "iOS Safari",
          "1.iPhone打开系统\n2.点击导出", "触发下载或弹出分享菜单", "无反应", 2,3,1,3,2,2);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("前端导出使用Blob+URL.createObjectURL移动端Safari不支持此方式");
        d = dft(DefectStatus.ANALYZED, engineer,
          "列表页长时间打开后浏览器内存占用>1GB", "缺陷列表页保持打开2小时浏览器内存从200MB升至1.2GB",
          "Chrome任务管理器显示该Tab内存1.2GB", "Chrome 125",
          "1.打开缺陷列表\n2.Chrome任务管理器观察\n3.等待2小时", "内存稳定在300MB内", "内存升至1.2GB", 4,3,3,5,2,3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("TanStack Query缓存未设置gcTime或组件未正确卸载导致数据累积");
        d = dft(DefectStatus.ANALYZED, engineer,
          "用户上传头像后其他用户看不到新头像", "修改头像后只有自己能看到新头像其他人看到的仍是旧头像",
          "A修改头像后B看到的A头像仍是默认头像", "全平台",
          "1.A上传新头像\n2.B登录查看A的头像", "B看到A的新头像", "B看到旧头像", 2,2,1,3,3,2);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("头像URL未添加版本参数浏览器使用缓存旧头像");
        d = dft(DefectStatus.ANALYZED, engineer,
          "Prometheus指标端口与业务端口混用", "指标/metrics暴露在业务端口8081未做独立端口",
          "Prometheus抓取/metrics走8081与业务请求共用一个线程池", "生产环境",
          "1.curl localhost:8081/metrics\n2.查看响应", "指标端口独立如8082", "无独立端口", 2,2,2,4,2,3);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("management.server.port未配置导致指标共用业务端口");
        d = dft(DefectStatus.ANALYZED, engineer,
          "列表页Tab切换时筛选条件被清空", "在状态筛选页签间切换时已设置的关键词等条件被重置",
          "筛选状态=TRIAGING后搜索关键词再点其他状态页签关键词消失", "全平台",
          "1.选择TRIAGING页签\n2.搜索关键词\n3.点ANALYZED页签", "关键词保留", "关键词清空", 2,3,2,4,3,2);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("Tab切换时重新初始化筛选状态未保留searchParams");
        d = dft(DefectStatus.ANALYZED, engineer,
          "知识库搜索不区分大小写导致英文搜索失效", "知识库搜索大小写敏感搜索Java和java结果不同",
          "搜索Java返回3条java返回0条预期结果相同", "全平台",
          "1.搜索Java\n2.搜索java\n3.对比结果", "两个搜索返回相同结果", "结果不一致", 1,2,1,3,3,2);
        fullTransition(d, DefectStatus.ANALYZED, "根因分析完成");
        d.setRootCauseHypothesis("KnowledgeRepository查询未使用LOWER()或数据库排序规则区分大小写");

        // ── PLANNED +6 ──────────────────────────────────────────────────
        d = dft(DefectStatus.PLANNED, engineer,
          "Maven依赖版本冲突导致NoClassDefFoundError", "引入多个第三方库后版本冲突运行时报NoClassDefFoundError",
          "启动时正常运行时调用某方法抛NoClassDefFoundError: com/google/common/collect/ImmutableList", "JDK21+Maven",
          "1.mvn dependency:tree\n2.查看冲突", "依赖版本统一无冲突", "版本冲突", 4,3,3,3,1,3);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已制定");
        d.setRootCauseHypothesis("多个库依赖不同版本Guava传递依赖引入旧版本覆盖新版本");
        d.setFixPlan("在pom.xml显式声明Guava版本号排除传递依赖的旧版本");
        d = dft(DefectStatus.PLANNED, engineer,
          "动态生成的SQL语句未用参数化查询", "报表模块拼接SQL未参数化存在二次SQL注入风险",
          "审计扫描发现报表模块存在拼接SQL代码", "全环境",
          "1.代码搜索String.concat拼接SQL\n2.查看报表模块", "所有SQL使用参数化或CriteriaAPI", "存在拼接SQL", 5,3,5,3,1,3);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已制定");
        d.setRootCauseHypothesis("报表模块为快速交付使用原生JDBC拼接SQL未用PrepareStatement");
        d.setFixPlan("改用Spring Data JPA Criteria API或至少使用?占位符+参数绑定");
        d = dft(DefectStatus.PLANNED, engineer,
          "定时任务在执行期间可被重复触发", "@Scheduled定时任务未加锁导致上一次未执行完新任务就启动",
          "清理临时文件定时任务配置每小时执行但某次执行超过1小时导致两个实例同时运行", "生产环境",
          "1.触发定时任务\n2.任务执行超1小时\n3.观察任务实例数", "同时只有一个实例运行", "两个实例同时运行", 3,2,3,2,1,3);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已制定");
        d.setRootCauseHypothesis("@Scheduled默认不检查上次是否完成未使用ShedLock等分布式锁");
        d.setFixPlan("引入ShedLock或使用数据库锁表确保定时任务单实例执行");
        d = dft(DefectStatus.PLANNED, engineer,
          "应用日志未输出TraceId导致分布式追踪困难", "跨服务调用时日志缺少TraceId无法追踪完整请求链路",
          "API网关→用户服务→缺陷服务链路中日志无统一TraceId", "微服务环境",
          "1.发起API请求\n2.查看各服务日志", "所有日志包含统一TraceId", "无TraceId", 2,2,2,4,2,3);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已制定");
        d.setRootCauseHypothesis("Logback MDC未配置TraceId自动注入Spring Cloud Sleuth未引入");
        d.setFixPlan("添加TraceIdFilter在请求入口生成UUID写入MDC所有日志自动携带");
        d = dft(DefectStatus.PLANNED, engineer,
          "release模式下React仍渲染开发警告", "生产构建后控制台仍有React DevTools警告信息泄露技术栈细节",
          "生产环境Console看到React开发警告暴露版本号", "生产环境",
          "1.打开生产环境\n2.查看Console", "无React警告", "有开发警告", 1,2,1,3,3,2);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已制定");
        d.setRootCauseHypothesis("Vite构建时mode未正确设置为production导致React开发包被引入");
        d.setFixPlan("vite build时添加--mode production确保NODE_ENV=production");
        d = dft(DefectStatus.PLANNED, engineer,
          "JWT过期后前端未自动刷新Token", "Token过期后所有API返回401前端未尝试刷新而是直接跳转登录页",
          "使用系统30分钟后突然跳转到登录页所有未保存内容丢失", "全平台",
          "1.登录使用系统\n2.等待Token过期\n3.继续操作", "自动刷新Token保持登录", "突然跳转登录页", 3,4,3,3,2,3);
        fullTransition(d, DefectStatus.PLANNED, "修复方案已制定");
        d.setRootCauseHypothesis("API client拦截器仅处理401重定向未在Token将过期时主动调用refresh接口");
        d.setFixPlan("在API client中添加Token过期前5分钟自动调用refresh接口逻辑");

        // ── IN_REPAIR +10 ───────────────────────────────────────────────
        d = dft(DefectStatus.IN_REPAIR, engineer,
          "PDF预览组件在Firefox下不兼容", "pdfjs-dist在Firefox中渲染PDF出现空白页",
          "Firefox 127打开PDF附件预览显示白屏", "Firefox 127",
          "1.Firefox打开系统\n2.上传PDF\n3.预览", "PDF正常预览", "白屏", 3,3,2,3,2,2);
        fullTransition(d, DefectStatus.IN_REPAIR, "修复中");
        d.setRootCauseHypothesis("pdfjs-dist Worker路径在Firefox下解析错误");
        d.setFixPlan("显式设置pdfjs.GlobalWorkerOptions.workerSrc");
        d = dft(DefectStatus.IN_REPAIR, engineer,
          "表单输入时搜索引擎输入法兼容问题", "使用搜狗输入法在Sheet面板输入时光标定位错误",
          "搜狗输入法输入时光标跳到文本框外面", "搜狗输入法 Win11",
          "1.切换搜狗输入法\n2.在缺陷描述编辑", "输入正常光标位置正确", "光标跳位", 2,3,1,4,2,2);
        fullTransition(d, DefectStatus.IN_REPAIR, "修复中");
        d.setRootCauseHypothesis("输入法composition事件与React受控组件冲突");
        d.setFixPlan("使用onCompositionStart/onCompositionEnd处理IME输入状态");
        d = dft(DefectStatus.IN_REPAIR, engineer,
          "数据库连接泄露在异常场景下未释放", "特定异常路径下数据库连接未归还连接池导致连接耗尽",
          "批量导入失败后连接池从20降至5然后请求开始排队", "生产环境",
          "1.执行批量导入\n2.模拟导入失败\n3.查看连接池", "异常后连接归还", "连接未归还", 5,4,5,2,1,4);
        fullTransition(d, DefectStatus.IN_REPAIR, "修复中");
        d.setRootCauseHypothesis("批量导入异常处理中未在finally块关闭EntityManager");
        d.setFixPlan("在批量导入方法添加try-finally确保EntityManager.close()");
        d = dft(DefectStatus.IN_REPAIR, engineer,
          "缺陷列表表格header与内容列对不齐", "某些分辨率下表格header列宽与内容列宽不一致",
          "1366×768分辨率下表头列与内容列错位5px", "Chrome 125",
          "1.调整窗口1366×768\n2.查看表格", "表头与内容对齐", "错位", 1,2,1,2,3,2);
        fullTransition(d, DefectStatus.IN_REPAIR, "修复中");
        d.setRootCauseHypothesis("TanStack Table columnSizing与CSS表格布局冲突");
        d.setFixPlan("使用table-fixed布局+统一ColumnSizing配置");
        d = dft(DefectStatus.IN_REPAIR, engineer,
          "复制粘贴表格内容到Excel时格式丢失", "从缺陷列表复制数据粘贴到Excel时所有内容挤在一个单元格",
          "选中列表数据Ctrl+C在Excel中Ctrl+V全部在A1单元格", "Excel 365",
          "1.选中列表多行\n2.Ctrl+C\n3.Excel Ctrl+V", "数据按行列粘贴", "全部在一格", 1,2,1,3,3,2);
        fullTransition(d, DefectStatus.IN_REPAIR, "修复中");
        d.setRootCauseHypothesis("表格使用DIV+CSS布局非原生table导致剪贴板不含表格结构");
        d.setFixPlan("使用原生table元素或添加data-table导出剪贴板时保持表格格式");
        d = dft(DefectStatus.IN_REPAIR, engineer,
          "API并发调用时代理超时配置不合理", "Nginx反向代理超时60s但后端DeepSeek调用最多180s导致504",
          "流转缺陷触发AI生成时代理超时返回504", "Nginx+Spring Boot",
          "1.流转缺陷触发AI\n2.查看Network", "正常返回或异步处理", "504 Gateway Timeout", 3,3,2,3,1,3);
        fullTransition(d, DefectStatus.IN_REPAIR, "修复中");
        d.setRootCauseHypothesis("Nginx proxy_read_timeout=60s小于DeepSeek调用超时180s");
        d.setFixPlan("修改Nginx proxy_read_timeout为300s或AI调用改为异步+轮询");
        d = dft(DefectStatus.IN_REPAIR, engineer,
          "前端埋点数据上报失败时阻塞主线程", "数据埋点SDK上报失败时同步重试阻塞了页面渲染",
          "埋点服务不可用时页面明显卡顿Performance显示Long Task", "全平台",
          "1.停掉埋点服务\n2.操作页面", "埋点失败不影响页面性能", "页面卡顿", 2,3,1,4,2,2);
        fullTransition(d, DefectStatus.IN_REPAIR, "修复中");
        d.setRootCauseHypothesis("埋点SDK使用同步XMLHttpRequest发送数据失败重试逻辑在主线程执行");
        d.setFixPlan("改为navigator.sendBeacon异步发送失败时写入localStorage延迟重试");
        d = dft(DefectStatus.IN_REPAIR, engineer,
          "Git合并冲突后前端构建产物包含冲突标记", "合并分支时冲突未完全解决dist包含<<<<<<<标记导致JS报错",
          "部署后页面白屏查看JS文件发现<<<<<<< HEAD残留", "CI/CD",
          "1.合并代码\n2.npm run build\n3.检查dist", "构建产物无冲突标记", "产物含冲突标记", 5,5,5,1,1,5);
        fullTransition(d, DefectStatus.IN_REPAIR, "修复中");
        d.setRootCauseHypothesis("CI流水线未检查构建产物中是否包含冲突标记");
        d.setFixPlan("在CI构建脚本中添加grep检查禁止构建产物包含<<<<<<<等冲突标记");
        d = dft(DefectStatus.IN_REPAIR, engineer,
          "WebSocket连接在移动网络切换时不断开重连", "WiFi切换到4G时WebSocket连接未检测到断开一直显示在线",
          "从WiFi切到4G后实时通知不更新显示在线但收不到消息", "移动端浏览器",
          "1.WiFi连接系统\n2.切到4G\n3.触发通知", "自动重连WebSocket", "连接假死", 3,4,2,3,3,2);
        fullTransition(d, DefectStatus.IN_REPAIR, "修复中");
        d.setRootCauseHypothesis("WebSocket未实现心跳检测和自动重连机制");
        d.setFixPlan("添加WebSocket心跳每30秒ping一次超时断开并自动重连");
        d = dft(DefectStatus.IN_REPAIR, engineer,
          "表单自动保存草稿功能覆盖了用户手动保存的数据", "自动保存草稿的时间戳晚于手动保存导致草稿覆盖了正式数据",
          "手动保存表单后自动保存草稿把已保存数据覆盖为空", "全平台",
          "1.填写表单\n2.手动点击保存\n3.等待自动保存触发", "自动保存不覆盖手动保存数据", "手动保存被覆盖", 3,3,2,3,2,2);
        fullTransition(d, DefectStatus.IN_REPAIR, "修复中");
        d.setRootCauseHypothesis("自动保存逻辑未比较lastManualSaveTime时间戳直接覆盖");
        d.setFixPlan("自动保存前检查lastManualSaveTime如在30秒内则跳过自动保存");

        // ── FIXED +8 ────────────────────────────────────────────────────
        d = dft(DefectStatus.FIXED, engineer,
          "内存泄漏：事件监听器在组件卸载后未移除", "在列表和详情间快速切换100次后内存增长500MB",
          "Chrome Performance录制显示Event Listeners累积", "Chrome 125",
          "1.打开Chrome Performance\n2.快速切换列表/详情100次\n3.录制内存", "内存稳定不增长", "内存持续增长", 4,3,4,2,1,3);
        fullTransition(d, DefectStatus.FIXED, "修复完成");
        d.setRootCauseHypothesis("useEffect中addEventListener未在cleanup中removeEventListener");
        d.setFixPlan("检查所有useEffect确保每个addEventListener都有对应的cleanup");
        d.setFixContent("全局搜索addEventListener确保都有对应的return()=>removeEventListener");
        d.setAffectedModules("前端多个组件");
        d.setFixDuration(120);
        d = dft(DefectStatus.FIXED, engineer,
          "LoadBalancer未配置健康检查导致请求打到故障节点", "上游服务故障后负载均衡仍转发请求导致部分请求失败",
          "后端服务A宕机后仍有30%请求被转发到A返回502", "K8s Service",
          "1.停止一个Pod\n2.观察请求成功率", "请求全部转发到健康Pod", "部分请求失败", 5,4,5,3,1,4);
        fullTransition(d, DefectStatus.FIXED, "修复完成");
        d.setRootCauseHypothesis("K8s Service未配置readinessProbe或LoadBalancer未使用podReadinessGates");
        d.setFixPlan("添加readinessProbe并在Service中配置publishNotReadyAddresses:false");
        d.setFixContent("Deployment添加readinessProbe: httpGet path=/actuator/health port=8081");
        d.setAffectedModules("K8s Deployment");
        d.setFixDuration(30);
        d = dft(DefectStatus.FIXED, engineer,
          "知识库条目发布后重新编辑内容未记录修改历史", "编辑已发布知识条目后保存旧内容被覆盖无法回退",
          "修改排查手册内容后想回退发现无历史版本", "全平台",
          "1.编辑已发布知识条目\n2.保存\n3.尝试查看历史版本", "支持查看和回退历史版本", "无历史版本", 2,3,1,3,3,2);
        fullTransition(d, DefectStatus.FIXED, "修复完成");
        d.setRootCauseHypothesis("KnowledgeItem表无版本控制字段更新直接覆盖content");
        d.setFixPlan("添加knowledge_item_versions表每次编辑追加新版本记录");
        d.setFixContent("创建KnowledgeItemVersion实体记录每次编辑的content快照+编辑人+编辑时间");
        d.setAffectedModules("KnowledgeItem实体, KnowledgeService");
        d.setFixDuration(90);
        d = dft(DefectStatus.FIXED, engineer,
          "properties配置文件中的密码明文存储", "application.properties中数据库密码和API Key明文存储",
          "代码仓库中application.properties包含root123和sk-b28...明文", "版本控制",
          "1.打开application.properties\n2.查看敏感配置", "敏感配置使用环境变量或加密存储", "明文密码", 5,2,5,5,1,4);
        fullTransition(d, DefectStatus.FIXED, "修复完成");
        d.setRootCauseHypothesis("项目未接入配置中心或环境变量敏感信息直接写在配置文件中");
        d.setFixPlan("敏感信息改为${ENV_VAR}占位符生产环境通过环境变量注入或使用Jasypt加密");
        d.setFixContent("修改application.properties: spring.datasource.password=${DB_PASSWORD}等");
        d.setAffectedModules("application.properties, Docker Compose");
        d.setFixDuration(45);
        d = dft(DefectStatus.FIXED, engineer,
          "用户提交缺陷后页面一直停留在创建弹窗", "提交缺陷成功后弹窗不关闭用户以为未提交又点了一次",
          "提交创建缺陷API返回200但弹窗未关闭", "Chrome 125",
          "1.填写缺陷表单\n2.点击提交\n3.观察弹窗", "弹窗关闭返回列表", "弹窗不关闭", 3,4,2,5,3,2);
        fullTransition(d, DefectStatus.FIXED, "修复完成");
        d.setRootCauseHypothesis("创建缺陷mutation的onSuccess回调中缺少关闭弹窗逻辑");
        d.setFixPlan("在onSuccess中添加setCreateDialogOpen(false)");
        d.setFixContent("在useCreateDefect的onSuccess中添加closeDialog()调用");
        d.setAffectedModules("前端 CreateDefectDialog");
        d.setFixDuration(15);
        d = dft(DefectStatus.FIXED, engineer,
          "Firefox浏览器下Date格式显示NaN", "Firefox中缺陷创建时间显示为NaN/NaN/NaN",
          "Firefox 127打开缺陷列表所有日期显示NaN", "Firefox 127",
          "1.Firefox打开系统\n2.查看缺陷列表日期列", "日期正常显示", "显示NaN", 4,4,3,3,2,3);
        fullTransition(d, DefectStatus.FIXED, "修复完成");
        d.setRootCauseHypothesis("日期字符串格式YYYY-MM-DD HH:mm:ss Firefox不支持直接用new Date解析");
        d.setFixPlan("日期解析改为new Date(Date.parse(str))或使用dayjs统一处理");
        d.setFixContent("全局日期处理函数使用dayjs(dateStr).format('YYYY-MM-DD HH:mm')");
        d.setAffectedModules("lib/utils.ts fmtDateTime");
        d.setFixDuration(20);
        d = dft(DefectStatus.FIXED, engineer,
          "滚动加载更多时数据重复", "列表页下拉加载更多时出现前面已展示的数据重复显示",
          "滚动到底部加载第2页数据但返回的仍是第1页数据", "全平台",
          "1.滚动列表到底\n2.触发加载更多\n3.查看新增数据", "新数据与已有数据不重复", "第1页数据重复出现", 2,3,2,4,2,2);
        fullTransition(d, DefectStatus.FIXED, "修复完成");
        d.setRootCauseHypothesis("分页参数page在加载更多后未递增始终为0");
        d.setFixPlan("修复加载更多逻辑pageIndex正确+1");
        d.setFixContent("const nextPage = page + 1; fetchDefects({page: nextPage})");
        d.setAffectedModules("前端列表分页逻辑");
        d.setFixDuration(10);
        d = dft(DefectStatus.FIXED, engineer,
          "镜像仓库推送带latest标签导致版本回退", "CI构建推送latest标签导致K8s拉取到旧版本镜像",
          "部署后功能与预期不符发现K8s Pod运行的是旧版本镜像", "K8s+CI",
          "1.推送新镜像\n2.K8s滚动更新\n3.检查Pod镜像", "Pod运行最新镜像", "运行旧镜像", 4,3,5,3,1,4);
        fullTransition(d, DefectStatus.FIXED, "修复完成");
        d.setRootCauseHypothesis("CI使用了latest标签且K8s imagePullPolicy为IfNotPresent拉取到缓存旧镜像");
        d.setFixPlan("CI构建使用git commit hash作为镜像标签K8s配置imagePullPolicy=Always");
        d.setFixContent("CI: docker tag app:${CI_COMMIT_SHA}; K8s: imagePullPolicy: Always");
        d.setAffectedModules("CI配置, K8s Deployment");
        d.setFixDuration(20);

        // ── VERIFIED +6 ─────────────────────────────────────────────────
        d = dft(DefectStatus.VERIFIED, qa,
          "事务超时配置过短导致大事务回滚", "默认事务超时30s导致批量更新1000条时事务超时回滚",
          "批量修改500条缺陷耗时35s被回滚报TransactionTimedOutException", "生产环境",
          "1.选中500条缺陷\n2.批量修改\n3.观察结果", "批量操作正常完成", "事务超时回滚", 3,3,3,2,1,3);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("Spring事务默认超时30s批量操作超时回滚但未做分批处理");
        d.setFixPlan("大事务分批提交每批100条+增加事务超时时间到120s");
        d.setFixContent("@Transactional(timeout=120); 数据分批每批100条commit一次");
        d.setAffectedModules("DefectService");
        d.setFixDuration(60);
        d.setVerificationResult("批量1000条操作正常完成无超时");
        d.setRegressionScope("所有批量操作");
        d.setVerificationConclusion("大事务分批处理有效");
        d = dft(DefectStatus.VERIFIED, qa,
          "构建产物未做完整性校验", "CI构建完成后未校验产物完整性导致部署损坏的Jar包",
          "CI网络波动导致下载依赖不完整部署后启动报Invalid byte tag in constant pool", "CI/CD",
          "1.CI构建\n2.下载产物\n3.部署", "部署正常启动", "启动报类加载错误", 5,5,5,2,1,4);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("CI流水线无构建产物校验步骤下载不完整仍视为成功");
        d.setFixPlan("构建后校验jar包sha256值与预期值比对不一致则重新构建");
        d.setFixContent("mvn package后执行sha256sum app.jar与cache比对");
        d.setAffectedModules("CI配置");
        d.setFixDuration(30);
        d.setVerificationResult("修改jar包后CI检测到hash不匹配重新构建");
        d.setRegressionScope("构建流水线");
        d.setVerificationConclusion("完整性校验有效防止部署损坏产物");
        d = dft(DefectStatus.VERIFIED, qa,
          "API版本未做兼容导致旧版前端报错", "后端API变更字段名后旧版前端未同步更新报undefined错误",
          "后端将userName改为displayName前端读取undefined页面报错", "生产环境",
          "1.部署新后端\n2.用旧前端访问\n3.查看错误", "API向后兼容或版本隔离", "前端报undefined错误", 4,4,3,3,1,3);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("API未做版本控制字段直接重命名破坏向后兼容");
        d.setFixPlan("API路径添加/v1前缀字段变更保留旧字段增加新字段标记@Deprecated");
        d.setFixContent("添加@RequestMapping(\"/api/v1\")保留旧字段3个月后移除");
        d.setAffectedModules("所有Controller");
        d.setFixDuration(60);
        d.setVerificationResult("旧前端请求/api/v1仍正常响应");
        d.setRegressionScope("所有API");
        d.setVerificationConclusion("API版本兼容性已建立");
        d = dft(DefectStatus.VERIFIED, qa,
          "日志脱敏未覆盖手机号和身份证号", "应用日志中用户手机号和身份证号明文输出违反隐私合规",
          "日志中发现phone=13812345678和idCard=310xxx明文", "生产环境",
          "1.搜索日志文件\n2.grep查找手机号正则", "敏感信息脱敏如phone=138****5678", "明文输出", 4,2,5,5,1,4);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("Logback pattern中未配置脱敏Converter");
        d.setFixPlan("自定义Logback MessageConverter对手机号/身份证/邮箱做正则脱敏");
        d.setFixContent("实现SensitiveDataConverter自动匹配替换\\d{3}\\d{4}\\d{4}等模式");
        d.setAffectedModules("Logback配置, 脱敏工具类");
        d.setFixDuration(60);
        d.setVerificationResult("日志中敏感信息已脱敏输出");
        d.setRegressionScope("所有日志输出");
        d.setVerificationConclusion("日志脱敏合规达标");
        d = dft(DefectStatus.VERIFIED, qa,
          "用户删除缺陷后附件目录未清理", "删除缺陷记录后uploads目录中对应附件文件夹未被删除",
          "删除3条缺陷后uploads下对应子目录仍存在占用磁盘空间", "生产环境",
          "1.创建缺陷并上传附件\n2.删除该缺陷\n3.检查uploads目录", "对应子目录被删除", "目录残留", 2,2,2,3,3,2);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("DefectService.deleteDefect只删除了Defect记录级联删除未触发FileService清理");
        d.setFixPlan("在deleteDefect方法中先获取所有附件路径递归删除目录再删除数据库");
        d.setFixContent("在deleteDefect中添加: fileService.deleteDirectory(defectId)");
        d.setAffectedModules("DefectService, FileService");
        d.setFixDuration(30);
        d.setVerificationResult("删除缺陷后uploads对应目录被清理");
        d.setRegressionScope("所有删除操作");
        d.setVerificationConclusion("磁盘空间正确释放");
        d = dft(DefectStatus.VERIFIED, qa,
          "Webpack/Vite Source Map在生产环境暴露源码", "生产环境部署包含.js.map文件浏览器可查看完整源码",
          "浏览器DevTools Sources面板可看到未混淆的完整TypeScript源码", "生产环境",
          "1.打开生产环境\n2.DevTools Sources\n3.查看代码", "生产环境无source map", "源码完全暴露", 4,2,5,5,1,4);
        fullTransition(d, DefectStatus.VERIFIED, "QA验证通过");
        d.setRootCauseHypothesis("Vite build默认生成sourcemap未在生产构建中禁用");
        d.setFixPlan("Vite配置build.sourcemap在production模式下设为false");
        d.setFixContent("vite.config.ts: build:{ sourcemap: process.env.NODE_ENV === 'production' ? false : true }");
        d.setAffectedModules("vite.config.ts");
        d.setFixDuration(10);
        d.setVerificationResult("生产环境Sources面板无源码");
        d.setRegressionScope("前端构建");
        d.setVerificationConclusion("源码不泄露");

        // ── CLOSED +12 ──────────────────────────────────────────────────
        d = dft(DefectStatus.CLOSED, qa,
          "Bean Validation校验注解未生效", "实体类添加@NotBlank等校验注解但Controller未加@Valid导致校验不生效",
          "创建缺陷时未填写标题也能成功提交", "全环境", "1.创建缺陷标题留空\n2.提交", "返回400校验错误", "提交成功", 4,3,4,5,1,3);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("Controller方法参数缺少@Valid注解Spring不会触发校验");
        d.setFixPlan("在所有Controller的@RequestBody参数前添加@Valid注解");
        d.setFixContent("添加@Valid: public ResponseEntity<?> create(@Valid @RequestBody CreateRequest req)");
        d.setAffectedModules("所有Controller");
        d.setFixDuration(30);
        d.setVerificationResult("未填标题返回400提示");
        d.setRegressionScope("所有API参数校验");
        d.setVerificationConclusion("Bean Validation生效");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "Bean Validation校验");
        d = dft(DefectStatus.CLOSED, qa,
          "应用启动时执行Flyway迁移失败未阻止启动", "数据库迁移脚本有错误但应用仍正常启动导致后续数据操作异常",
          "迁移脚本V2__add_col.sql语法错误应用启动成功但后续查询报Unknown column", "生产环境",
          "1.部署含错误迁移脚本版本\n2.启动\n3.查询操作", "迁移失败应用启动失败", "应用启动但查询报错", 5,4,5,3,1,4);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("spring.flyway.fail-on-migration-scripts-error未配置默认为false");
        d.setFixPlan("设置spring.flyway.fail-on-migration-error=true阻止启动");
        d.setFixContent("application.properties添加: spring.flyway.fail-on-migration-error=true");
        d.setAffectedModules("application.properties");
        d.setFixDuration(10);
        d.setVerificationResult("错误迁移脚本导致启动失败报明确错误");
        d.setRegressionScope("数据库迁移");
        d.setVerificationConclusion("迁移失败时安全停止启动");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "数据库迁移安全策略");
        d = dft(DefectStatus.CLOSED, qa,
          "Spring Boot内嵌Tomcat最大线程数不足", "默认200线程在生产流量下不足导致请求排队超时",
          "高峰期并发500时大量请求处于Pending状态超时", "生产环境",
          "1.高峰期观察/actuator/metrics\n2.查看http.server.requests", "请求正常处理", "请求排队超时", 4,4,4,2,1,3);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("server.tomcat.threads.max默认200未根据实际流量调优");
        d.setFixPlan("调整server.tomcat.threads.max=500并添加max-connections=10000");
        d.setFixContent("application.properties: server.tomcat.threads.max=500; server.tomcat.max-connections=10000");
        d.setAffectedModules("application.properties");
        d.setFixDuration(15);
        d.setVerificationResult("500并发下请求正常处理无排队超时");
        d.setRegressionScope("所有API");
        d.setVerificationConclusion("线程池配置满足业务需求");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "Tomcat线程池调优");
        d = dft(DefectStatus.CLOSED, qa,
          "Hibernate二级缓存配置导致脏读", "启用二级缓存后查询返回旧数据未感知数据库变更",
          "管理员修改缺陷优先级后列表仍显示旧优先级", "生产环境",
          "1.启用Hibernate二级缓存\n2.修改缺陷优先级\n3.查询列表", "显示最新数据", "显示旧数据", 4,3,4,5,1,3);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("Hibernate二级缓存未配置失效策略更新操作后缓存未清除");
        d.setFixPlan("关闭实体级二级缓存改用Query Cache或只在读多写少场景启用");
        d.setFixContent("移除@Cacheable注解改用Spring Cache在更新时显式@CacheEvict");
        d.setAffectedModules("Hibernate配置, DefectService");
        d.setFixDuration(60);
        d.setVerificationResult("修改后列表立即显示最新数据");
        d.setRegressionScope("所有查询");
        d.setVerificationConclusion("缓存一致性保证");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "二级缓存脏读修复");
        d = dft(DefectStatus.CLOSED, qa,
          "用户切换组织后权限未更新", "用户从A组织切换到B组织后仍看到A组织的数据",
          "多租户切换组织后列表仍显示原组织缺陷", "多租户环境",
          "1.用户切换组织\n2.查看缺陷列表", "显示新组织数据", "显示旧组织数据", 4,3,4,5,2,3);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("组织切换后JWT Token未更新仍携带旧orgId");
        d.setFixPlan("组织切换后刷新Token或由前端传当前orgId作为请求头");
        d.setFixContent("添加X-Current-Org-Id请求头后端据此过滤数据");
        d.setAffectedModules("JwtUtil, 数据查询拦截器");
        d.setFixDuration(60);
        d.setVerificationResult("切换组织后数据正确隔离");
        d.setRegressionScope("多租户数据隔离");
        d.setVerificationConclusion("租户隔离正确");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "多租户数据隔离");
        d = dft(DefectStatus.CLOSED, qa,
          "前端路由懒加载配置错误导致打包未分割", "所有路由组件打包到同一个chunk懒加载未生效",
          "npm run build后只有一个index-xxx.js 2MB+", "Vite 8",
          "1.npm run build\n2.分析dist产物", "路由组件按需加载分多个chunk", "一个chunk", 2,3,2,3,2,2);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("TanStack Router的lazy loaded route配置错误组件被同步import");
        d.setFixPlan("使用route.lazy(()=>import('./Component'))异步加载路由组件");
        d.setFixContent("修改routeTree配置: component: () => import('./routes/defects.$id')");
        d.setAffectedModules("router.tsx, routeTree配置");
        d.setFixDuration(30);
        d.setVerificationResult("dist生成多个独立chunk主bundle<500KB");
        d.setRegressionScope("构建产物");
        d.setVerificationConclusion("Code splitting生效");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "路由懒加载优化");
        d = dft(DefectStatus.CLOSED, qa,
          "K8s资源限制未配置导致Pod OOMKilled", "未设置memory limit导致Pod内存超限被K8s OOM Kill",
          "Pod频繁重启describe显示OOMKilled", "K8s生产集群",
          "1.kubectl describe pod\n2.查看Last State", "Pod稳定运行", "OOMKilled重启", 5,4,5,3,1,4);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("Deployment未设置resources.limits.memory");
        d.setFixPlan("设置memory limit为512Mi request为256Mi");
        d.setFixContent("resources: limits: memory: 512Mi; requests: memory: 256Mi");
        d.setAffectedModules("K8s Deployment");
        d.setFixDuration(15);
        d.setVerificationResult("Pod内存使用被限制在512Mi内");
        d.setRegressionScope("Pod稳定性");
        d.setVerificationConclusion("OOMKilled不再发生");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "K8s资源配置最佳实践");
        d = dft(DefectStatus.CLOSED, qa,
          "应用无优雅降级机制强依赖所有外部服务", "DeepSeek API不可用时缺陷流转功能也无法使用",
          "DeepSeek服务故障时流转接口报500", "生产环境",
          "1.停止DeepSeek服务\n2.尝试流转缺陷", "降级模式流转成功AI功能提示不可用", "流转失败500", 5,5,5,2,1,5);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("DefectService流转方法无外部服务降级逻辑AI调用失败即抛异常");
        d.setFixPlan("AI调用外层try-catch失败时记录日志但不阻塞流转返回提示'AI建议稍后生成'");
        d.setFixContent("在aiTrigger调用外包裹try-catch捕获异常后记录WARN日志不向上抛");
        d.setAffectedModules("DefectService, AITriggerService");
        d.setFixDuration(30);
        d.setVerificationResult("DeepSeek不可用时流转仍正常完成提示AI稍后可用");
        d.setRegressionScope("所有依赖外部服务流程");
        d.setVerificationConclusion("优雅降级生效");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "外部服务降级策略");
        d = dft(DefectStatus.CLOSED, qa,
          "生产环境使用默认Spring Security用户密码", "application.properties未修改Spring Security默认用户密码",
          "生产环境可user/控制台生成的密码登录actuator", "生产环境", "1.尝试user/随机密码登录\n2.成功进入", "默认用户被禁用", "默认用户可登录", 5,4,5,5,1,5);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("未配置spring.security.user.name覆盖默认值");
        d.setFixPlan("禁用Spring Security默认UserDetailsService并移除自动生成的默认密码");
        d.setFixContent("spring.security.user.name=DISABLED; @Bean InMemoryUserDetailsManager返回空");
        d.setAffectedModules("SecurityConfig");
        d.setFixDuration(20);
        d.setVerificationResult("默认用户无法登录");
        d.setRegressionScope("安全认证");
        d.setVerificationConclusion("默认凭据已禁用");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "Spring Security默认凭据禁用");
        d = dft(DefectStatus.CLOSED, qa,
          "用户输入未限制特殊字符导致正则ReDoS攻击", "搜索输入特定正则模式导致CPU 100%触发ReDoS",
          "搜索框输入(a+)+b等30字符后服务CPU飙升100%", "生产环境",
          "1.搜索框输入(a+)+$等模式\n2.观察CPU", "输入校验拒绝危险正则", "CPU 100%", 5,3,5,3,1,4);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("用户输入直接传给Pattern.compile未做安全校验");
        d.setFixPlan("禁止用户输入正则表达式对输入长度限制为200字符");
        d.setFixContent("搜索接口添加@Size(max=200)校验且不使用用户输入作为正则");
        d.setAffectedModules("搜索接口, 输入校验");
        d.setFixDuration(20);
        d.setVerificationResult("ReDoS攻击无效服务CPU正常");
        d.setRegressionScope("所有搜索接口");
        d.setVerificationConclusion("ReDoS防护有效");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "ReDoS攻击防护");
        d = dft(DefectStatus.CLOSED, qa,
          "缓存雪崩：大量缓存同时过期打到数据库", "多个热点数据缓存设置了相同过期时间同时失效导致数据库压力骤增",
          "缓存到期时间都设在整点整点瞬间数据库连接数飙升", "生产环境",
          "1.设置10个热点缓存相同TTL\n2.等待到期\n3.观察数据库连接", "缓存失效时间随机分散", "数据库瞬间高负载", 4,4,4,3,1,3);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("所有缓存TTL设置为相同值同时过期");
        d.setFixPlan("缓存TTL添加5-15分钟随机偏移量避免集中过期");
        d.setFixContent("cacheManager配置: expireAfterWrite(Duration.ofMinutes(30+ThreadLocalRandom.current().nextInt(15)))");
        d.setAffectedModules("CacheConfig");
        d.setFixDuration(30);
        d.setVerificationResult("缓存过期时间分散数据库压力平稳");
        d.setRegressionScope("所有缓存配置");
        d.setVerificationConclusion("缓存雪崩风险消除");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "缓存雪崩防护");
        d = dft(DefectStatus.CLOSED, qa,
          "错误页面泄露技术栈版本信息", "Whitelabel Error Page暴露Spring Boot 3.4.0等版本信息",
          "访问不存在路径返回Whitelabel Error Page含Spring Boot版本", "生产环境",
          "1.访问随机路径\n2.查看错误页面", "自定义错误页不泄露技术栈", "暴露版本信息", 2,2,4,5,1,4);
        fullTransition(d, DefectStatus.CLOSED, "QA已关闭缺陷");
        d.setRootCauseHypothesis("未配置自定义错误页面Spring Boot默认Whitelabel Error Page");
        d.setFixPlan("关闭Whitelabel Error Page配置自定义全局错误处理");
        d.setFixContent("server.error.whitelabel.enabled=false; 创建自定义ErrorController返回统一错误JSON");
        d.setAffectedModules("application.properties, ErrorController");
        d.setFixDuration(20);
        d.setVerificationResult("错误响应不泄露技术栈信息");
        d.setRegressionScope("所有错误响应");
        d.setVerificationConclusion("信息泄露风险消除");
        d.setClosedAt(LocalDateTime.now());
        addKnowledge(d, "错误页面信息泄露修复");

        // ── REOPENED +2 ─────────────────────────────────────────────────
        d = dft(DefectStatus.REOPENED, engineer,
          "验证时发现之前修复的XSS过滤可被绕过", "DOMPurify过滤后可被SVG标签的onload事件绕过",
          "<svg onload=alert(1)>通过DOMPurify过滤仍执行", "全平台",
          "1.输入<svg onload=alert(1)>\n2.提交\n3.查看效果", "SVG事件被过滤", "alert弹出", 5,4,5,3,1,5);
        fullTransition(d, DefectStatus.REOPENED, "验证不通过重新打开");
        d.setRootCauseHypothesis("DOMPurify默认配置未移除SVG事件属性需显式配置");
        d.setFixPlan("DOMPurify配置添加FORBID_TAGS:[svg]+FORBID_ATTR:[onload]");
        d.setFixContent("DOMPurify.sanitize(input, {FORBID_TAGS:['svg','math'], FORBID_ATTR:['onload','onerror']})");
        d.setAffectedModules("前端XSS过滤");
        d = dft(DefectStatus.REOPENED, engineer,
          "修复后SQL注入防护在特定编码下可被绕过", "之前修复的SQL注入在使用URL编码%27时可绕过转义",
          "输入%27%20OR%201=1--被URL解码后绕过转义", "全平台",
          "1.输入URL编码的SQL注入payload\n2.观察响应", "特殊字符被正确转义", "注入成功返回异常数据", 5,4,5,3,1,5);
        fullTransition(d, DefectStatus.REOPENED, "验证不通过重新打开");
        d.setRootCauseHypothesis("转义函数在URL解码之前执行导致解码后的特殊字符未转义");
        d.setFixPlan("先URL解码再对特殊字符转义确保双重编码无法绕过");
        d.setFixContent("input = URLDecoder.decode(input, UTF-8); input = escapeSql(input)");
        d.setAffectedModules("DefectRepository SQL转义工具");
    }
}
