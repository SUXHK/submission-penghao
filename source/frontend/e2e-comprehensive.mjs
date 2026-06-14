import { chromium } from 'playwright';

const BASE = 'http://localhost:3001';
let pass = 0, fail = 0;
function ok(name, cond, detail) {
  if (cond) { console.log(`  ✅ ${name}`); pass++; }
  else { console.log(`  ❌ ${name}: ${detail || ''}`); fail++; }
}
const wait = ms => new Promise(r => setTimeout(r, ms));
const TITLE = 'E2E-Full-' + Date.now();
let defectId = null;

// ── API Helpers ──
async function api(path, method, token, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } };
  if (body) opts.body = JSON.stringify(body);
  const resp = await fetch(`${BASE}${path}`, opts);
  return { status: resp.status, data: await resp.json().catch(() => ({})) };
}
const apiGet = (p, t) => api(p, 'GET', t);
const apiPost = (p, t, b) => api(p, 'POST', t, b);
const apiPut = (p, t, b) => api(p, 'PUT', t, b);
const apiPatch = (p, t) => api(p, 'PATCH', t);

// ── UI Helpers ──
async function login(page, username, password = 'admin123') {
  await page.goto(BASE + '/login');
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.waitForSelector('input[type="password"]', { timeout: 10000 });
  await wait(400);
  await page.getByRole('textbox').first().fill(username);
  await page.locator('input[type="password"]').first().fill(password);
  await page.getByRole('button', { name: '登录' }).click();
  await page.waitForURL('**/');
  await wait(600);
}

async function searchAndOpen(page, keyword) {
  await page.goto(BASE + '/'); await wait(800);
  const s = page.locator('input[placeholder="搜索…"]');
  await s.waitFor({ state: 'visible', timeout: 5000 });
  await s.fill(keyword.substring(0, 25));
  await wait(1500);
  const row = page.locator('tbody tr', { hasText: keyword.substring(0, 13) }).first();
  await row.waitFor({ state: 'visible', timeout: 8000 });
  await row.click({ force: true });
  await page.waitForSelector('text=复现信息', { timeout: 10000 });
  await wait(500);
}

async function editFieldUI(page, labelText, value) {
  // Use evaluate to find and click the editable area
  const clicked = await page.evaluate(({ label, val }) => {
    // Find all spans, locate the one with matching text
    const spans = document.querySelectorAll('span');
    let targetSpan = null;
    for (const s of spans) {
      if (s.textContent && s.textContent.trim().startsWith(label)) {
        targetSpan = s;
        break;
      }
    }
    if (!targetSpan) return 'no-label';

    // Find the clickable display div (with cursor-pointer) inside the field container
    const fieldDiv = targetSpan.closest('.border-b, [class*="border-b"]');
    if (!fieldDiv) return 'no-field';

    const clickable = fieldDiv.querySelector('[class*="cursor-pointer"]');
    if (!clickable) return 'no-clickable';

    clickable.click();
    return 'clicked';
  }, { label: labelText, val: value });

  if (clicked !== 'clicked') {
    console.log(`    ⚠️ editField(${labelText}): ${clicked}`);
    return false;
  }
  await wait(500);

  // Fill the visible input/textarea
  const ta = page.locator('textarea').first();
  const inp = page.locator('input[type="number"]').first();
  if (await ta.isVisible({ timeout: 500 }).catch(() => false)) {
    await ta.fill(value);
  } else if (await inp.isVisible({ timeout: 500 }).catch(() => false)) {
    await inp.fill(value);
  }

  await page.getByRole('button', { name: '保存' }).click({ force: true });
  await wait(800);
  return true;
}

async function transitionUI(page, targetLabel) {
  // Debug: dump all visible buttons in left column
  const buttons = await page.evaluate(() => {
    const leftCol = document.querySelector('[class*="w-\\[164px\\]"]');
    if (!leftCol) return 'no-left-col';
    const btns = leftCol.querySelectorAll('button');
    return Array.from(btns).map(b => ({ text: b.textContent?.trim(), disabled: b.disabled }));
  });
  console.log(`    时间线按钮: ${JSON.stringify(buttons)}`);

  // Scroll timeline to bottom
  await page.evaluate(() => {
    const cols = document.querySelectorAll('[class*="w-\\[164px\\]"]');
    for (const c of cols) c.scrollTop = c.scrollHeight;
  });
  await wait(500);

  const btn = page.locator('button').filter({ hasText: `→ ${targetLabel}` }).first();
  await btn.waitFor({ state: 'visible', timeout: 8000 });
  await btn.click({ force: true });
  await wait(500);

  const confirmBtn = page.locator('button').filter({ hasText: '确认流转' }).first();
  await confirmBtn.waitFor({ state: 'visible', timeout: 10000 });
  await confirmBtn.click({ force: true });
  // AI is now async, wait briefly for transition
  await wait(2000);
}

// ═════════════════════════════════
async function main() {
  console.log('╔══════════════════════════════════════╗');
  console.log('║  缺陷分诊 全面 E2E 自动化测试       ║');
  console.log('║  覆盖: 创建→流转→AI→验证→关闭     ║');
  console.log('╚══════════════════════════════════════╝\n');

  const browser = await chromium.launch({ headless: false, slowMo: 60 });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on('dialog', async d => { console.log('  ⚠️ Alert:', d.message()); await d.dismiss(); });

  try {
    // ════════════════ PHASE 1: Login ════════════════
    console.log('\n── 1. 角色登录 ──');
    await login(page, 'submitter');
    ok('Submitter 登录', await page.getByRole('heading', { name: '缺陷列表' }).isVisible());
    await login(page, 'engineer');
    ok('Engineer 登录', await page.getByRole('button', { name: '退出' }).isVisible());
    await login(page, 'qa');
    ok('QA 登录', await page.getByRole('heading', { name: '缺陷列表' }).isVisible());

    const tokens = {};
    await login(page, 'submitter'); tokens.submitter = await page.evaluate(() => localStorage.getItem('token'));
    await login(page, 'engineer'); tokens.engineer = await page.evaluate(() => localStorage.getItem('token'));
    await login(page, 'qa'); tokens.qa = await page.evaluate(() => localStorage.getItem('token'));

    // ════════════════ PHASE 2: Create ════════════════
    console.log('\n── 2. 创建缺陷 + 复现信息 ──');
    await login(page, 'submitter');

    await page.getByRole('button', { name: '创建缺陷' }).click();
    await wait(500);
    ok('创建弹窗已打开', await page.getByRole('heading', { name: '创建缺陷' }).isVisible());

    // Test: empty submit should be blocked
    await page.getByRole('button', { name: '提交分诊' }).click({ force: true });
    await wait(500);
    const stillOnForm = await page.getByRole('heading', { name: '创建缺陷' }).isVisible().catch(() => false);
    ok('空标题提交被阻止', stillOnForm, '表单应该还在');

    // Fill basic info
    await page.getByRole('textbox').first().fill(TITLE);
    const tas = page.locator('textarea');
    await tas.nth(0).fill('登录超时白屏无提示');
    await tas.nth(1).fill('用户超时后页面白屏');
    await page.locator('input[placeholder*="Chrome"]').first().fill('Chrome 125, Windows 11');
    await tas.nth(2).fill('1. 打开登录\n2. 输入账号\n3. 等超时');
    await tas.nth(3).fill('提示重新登录');
    await tas.nth(4).fill('白屏无任何提示');

    await page.getByRole('button', { name: '提交分诊' }).click({ force: true });
    await wait(2000);
    const sheetOpen = await page.getByText('复现信息', { exact: true }).isVisible().catch(() => false);
    ok('缺陷创建并提交成功', sheetOpen);

    const idText = await page.locator('span', { hasText: /^ID:\s*\d+$/ }).first().textContent();
    defectId = idText.match(/(\d+)/)?.[1];
    console.log(`  📋 缺陷 ID: ${defectId}`);
    ok('获取缺陷ID', !!defectId);

    // ════════════════ PHASE 3: Assessment + Validation ════════════════
    console.log('\n── 3. 后端校验测试 ──');

    // 3.1 Test: 6 required fields validation
    // Create a draft defect without filling required fields, try to submit
    const draftResp = await apiPost('/api/defects', tokens.submitter, {
      title: 'Draft-Test-' + Date.now(),
      description: 'test',
      phenomenon: '', environment: '', reproductionSteps: '', expectedResult: '', actualResult: ''
    });
    ok('草稿创建成功', draftResp.status === 200);
    const draftId = draftResp.data.id;

    if (draftId) {
      const subResp = await apiPatch(`/api/defects/${draftId}/transition?to=REPORTED`, tokens.submitter);
      ok('缺少必填字段被拒 (DRAFT→REPORTED)',
         subResp.status === 400 && subResp.data.error?.includes('必填'),
         JSON.stringify(subResp.data));
    }

    // 3.2 Test: assessment dimensions required for TRIAGING→ANALYZED
    const triageDefect = await apiPost('/api/defects', tokens.submitter, {
      title: 'Triage-Test-' + Date.now(),
      description: 'test',
      phenomenon: 'test', environment: 'test', reproductionSteps: 'test',
      expectedResult: 'test', actualResult: 'test'
    });
    const triageId = triageDefect.data.id;
    await apiPatch(`/api/defects/${triageId}/transition?to=REPORTED`, tokens.submitter);
    await apiPatch(`/api/defects/${triageId}/transition?to=TRIAGING`, tokens.engineer);

    const analyzeResp = await apiPatch(`/api/defects/${triageId}/transition?to=ANALYZED`, tokens.engineer);
    ok('缺少评估维度被拒 (TRIAGING→ANALYZED)',
       analyzeResp.status === 400 && analyzeResp.data.error?.includes('评估'),
       JSON.stringify(analyzeResp.data));

    // 3.3 Test: non-numeric type validation
    const badUpdate = await apiPut(`/api/defects/${defectId}`, tokens.engineer, { fixDuration: 'abc' });
    ok('非数字字段被拒 (400)', badUpdate.status === 400, JSON.stringify(badUpdate.data));

    // 3.4 Test: root cause required for ANALYZED→PLANNED
    await apiPut(`/api/defects/${triageId}`, tokens.engineer, {
      userImpact: 4, businessImpact: 3, frequency: 5, workaround: 2, releaseWindow: 1
    });
    await apiPatch(`/api/defects/${triageId}/transition?to=ANALYZED`, tokens.engineer);
    const planResp = await apiPatch(`/api/defects/${triageId}/transition?to=PLANNED`, tokens.engineer);
    ok('缺少根因假设被拒 (ANALYZED→PLANNED)',
       planResp.status === 400 && planResp.data.error?.includes('根因'),
       JSON.stringify(planResp.data));

    // Cleanup test defects
    if (draftId) await fetch(`${BASE}/api/defects/${draftId}`, { method: 'DELETE', headers: { 'Authorization': `Bearer ${tokens.submitter}` } }).catch(() => {});
    if (triageId) await fetch(`${BASE}/api/defects/${triageId}`, { method: 'DELETE', headers: { 'Authorization': `Bearer ${tokens.submitter}` } }).catch(() => {});

    // ════════════════ PHASE 4: Full Flow UI ════════════════
    console.log('\n── 4. UI 全流程流转 ──');
    await login(page, 'engineer');
    await searchAndOpen(page, TITLE);

    // 4.1 Verify current status via API
    let statusResp = await apiGet(`/api/defects/${defectId}`, tokens.engineer);
    console.log(`  当前状态: ${statusResp.data?.status}`);
    ok('缺陷处于 REPORTED 状态', statusResp.data?.status === 'REPORTED',
       `实际: ${statusResp.data?.status}`);

    // 4.2 REPORTED→TRIAGING
    await transitionUI(page, '分诊中');
    statusResp = await apiGet(`/api/defects/${defectId}`, tokens.engineer);
    console.log(`  当前状态: ${statusResp.data?.status}`);
    ok('→ 分诊中', statusResp.data?.status === 'TRIAGING',
       `实际状态: ${statusResp.data?.status}`);

    // 4.3 Fill assessment via API + TRIAGING→ANALYZED via UI
    console.log('  填写影响评估 (API)...');
    const putResp = await apiPut(`/api/defects/${defectId}`, tokens.engineer, {
      userImpact: 4, businessImpact: 3, frequency: 5, workaround: 2, releaseWindow: 1
    });
    ok('影响评估已填写', putResp.status === 200, `HTTP ${putResp.status}`);
    console.log(`    缺陷状态: ${putResp.data?.status}`);

    // Reload and transition via UI
    await page.goto(BASE + '/'); await wait(800);
    await searchAndOpen(page, TITLE);

    await transitionUI(page, '已分析');
    ok('→ 已分析 (优先级已计算)', await page.getByText('已分析', { exact: true }).first().isVisible().catch(() => false));

    // 4.4 Fill fields via API → UI transition
    await apiPut(`/api/defects/${defectId}`, tokens.engineer, {
      rootCauseHypothesis: '超时异常未catch导致Promise rejection白屏',
      fixPlan: '添加try-catch处理超时异常并显示友好提示',
      fixContent: '修改LoginController超时处理逻辑，添加异常捕获',
      affectedModules: 'LoginController, SessionManager',
      fixDuration: 120
    });

    // ANALYZED→PLANNED
    await page.goto(BASE + '/'); await wait(800);
    await searchAndOpen(page, TITLE);
    await transitionUI(page, '已计划');
    ok('→ 已计划', await page.getByText('已计划', { exact: true }).first().isVisible().catch(() => false));

    // PLANNED→IN_REPAIR
    await transitionUI(page, '修复中');
    ok('→ 修复中', await page.getByText('修复中', { exact: true }).first().isVisible().catch(() => false));

    // IN_REPAIR→FIXED
    await transitionUI(page, '已修复');
    ok('→ 已修复', await page.getByText('已修复', { exact: true }).first().isVisible().catch(() => false));

    // ════════════════ PHASE 5: Permission ════════════════
    console.log('\n── 5. 权限验证 ──');

    // ENGINEER cannot go FIXED→IN_REPAIR or FIXED→VERIFIED
    const engBack = await apiPatch(`/api/defects/${defectId}/transition?to=IN_REPAIR`, tokens.engineer);
    ok('ENGINEER FIXED→修复中被拒 (403)', engBack.status === 403, JSON.stringify(engBack.data));

    const engVerify = await apiPatch(`/api/defects/${defectId}/transition?to=VERIFIED`, tokens.engineer);
    ok('ENGINEER FIXED→已验证被拒 (403)', engVerify.status === 403, JSON.stringify(engVerify.data));

    // ════════════════ PHASE 6: QA Verify + Close ════════════════
    console.log('\n── 6. QA 验证 → 关闭 ──');
    await login(page, 'qa');

    // Fill verification via API (more reliable for multi-field)
    await apiPut(`/api/defects/${defectId}`, tokens.qa, {
      verificationResult: '修复后超时提示正常显示',
      regressionScope: '登录流程、超时处理',
      verificationConclusion: '修复有效可关闭'
    });

    await searchAndOpen(page, TITLE);
    await transitionUI(page, '已验证');
    ok('→ 已验证', await page.getByText('已验证', { exact: true }).first().isVisible().catch(() => false));

    await transitionUI(page, '已关闭');
    ok('→ 已关闭', await page.getByText('已关闭', { exact: true }).first().isVisible().catch(() => false));

    // ════════════════ PHASE 7: AI Suggestions ════════════════
    console.log('\n── 7. AI 建议审核 ──');
    await login(page, 'engineer');
    // Reopen → go back to REPORTED
    // First, let's check the closed defect's AI suggestions via API
    await wait(3000); // Wait for knowledge generation
    const aiResp = await apiGet(`/api/defects/${defectId}/ai-suggestions`, tokens.engineer);
    ok('AI 建议已生成', Array.isArray(aiResp.data) && aiResp.data.length > 0,
       `${aiResp.data?.length || 0} 条建议`);

    if (aiResp.data?.length > 0) {
      // Test review: accept a pending suggestion
      const pending = aiResp.data.find(s => s.status === 'PENDING_REVIEW');
      if (pending) {
        const acceptResp = await apiPut(`/api/ai-suggestions/${pending.id}/review`, tokens.engineer, {
          status: 'ACCEPTED'
        });
        ok('AI 建议已采纳', acceptResp.status === 200, JSON.stringify(acceptResp.data));
      }

      // Test reject
      const anotherPending = aiResp.data.filter(s => s.status === 'PENDING_REVIEW')[1];
      if (anotherPending) {
        const rejectResp = await apiPut(`/api/ai-suggestions/${anotherPending.id}/review`, tokens.engineer, {
          status: 'REJECTED',
          reviewNote: 'AI分析不符合当前场景'
        });
        ok('AI 建议已拒绝', rejectResp.status === 200, JSON.stringify(rejectResp.data));
      }

      // Test regenerate
      if (aiResp.data[0]) {
        const type = aiResp.data[0].type;
        const regenResp = await apiPost(`/api/defects/${defectId}/ai-suggestions?type=${type}`, tokens.engineer);
        ok('AI 建议已重新生成', regenResp.status === 200, JSON.stringify(regenResp.data));
      }
    }

    // ════════════════ PHASE 8: Reopen + Return ════════════════
    console.log('\n── 8. 重新打开 + 权限测试 ──');

    await login(page, 'engineer');
    await searchAndOpen(page, TITLE);

    const reopenBtn = page.locator('button').filter({ hasText: '重新打开' }).first();
    const canReopen = await reopenBtn.isVisible({ timeout: 3000 }).catch(() => false);
    if (canReopen) {
      await transitionUI(page, '重新打开');
      ok('→ 重新打开', await page.getByText('重新打开').first().isVisible().catch(() => false));

      await wait(1000);
      const reportBtn = page.locator('button').filter({ hasText: '已登记' }).first();
      if (await reportBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await transitionUI(page, '已登记');
        ok('→ 已登记', await page.getByText('已登记', { exact: true }).first().isVisible().catch(() => false));
      }
    } else {
      console.log('  ⚠️ 重新打开按钮不可见（可能已被重新打开）');
    }

    // ════════════════ PHASE 9: Audit + Knowledge ════════════════
    console.log('\n── 9. 审计日志 + 知识库 ──');

    // 9.1 Transition audit
    const trans = await apiGet(`/api/defects/${defectId}/transitions`, tokens.engineer);
    ok('审计日志可访问', trans.status === 200);
    ok('审计日志≥8条', Array.isArray(trans.data) && trans.data.length >= 8,
       `${trans.data?.length || 0} 条`);

    if (Array.isArray(trans.data)) {
      for (const exp of ['DRAFT→REPORTED','REPORTED→TRIAGING','TRIAGING→ANALYZED',
        'ANALYZED→PLANNED','PLANNED→IN_REPAIR','IN_REPAIR→FIXED',
        'FIXED→VERIFIED','VERIFIED→CLOSED']) {
        const [f,t] = exp.split('→');
        ok(`审计: ${exp}`, trans.data.some(x => x.fromStatus === f && x.toStatus === t));
      }
    }

    // 9.2 Knowledge base
    await wait(3000);
    const ki = await apiGet('/api/knowledge', tokens.qa);
    ok('知识库可访问', ki.status === 200);
    ok('知识条目已生成', Array.isArray(ki.data) && ki.data.length > 0,
       `${ki.data?.length || 0} 条`);

    // 9.3 Priority validation
    const d = await apiGet(`/api/defects/${defectId}`, tokens.engineer);
    if (d.status === 200 && d.data) {
      ok('优先级已自动计算', !!d.data.priority, `优先级: ${d.data.priority}`);
      console.log(`  加权公式: UI(${d.data.userImpact})*0.3 + BI(${d.data.businessImpact})*0.3 + F(${d.data.frequency})*0.2 + W(${d.data.workaround})*0.1 + RW(${d.data.releaseWindow})*0.1`);
    }

    // ════════════════ PHASE 10: UI Timeline ════════════════
    console.log('\n── 10. 时间线 UI 验证 ──');
    await login(page, 'qa');
    await searchAndOpen(page, TITLE);
    await wait(500);

    let visibleStates = 0;
    for (const s of ['草稿','已登记','分诊中','已分析','已计划','修复中','已修复','已验证','已关闭']) {
      if (await page.getByText(s, { exact: true }).first().isVisible({ timeout: 1000 }).catch(() => false)) visibleStates++;
    }
    ok('时间线完整显示', visibleStates >= 8, `${visibleStates}/9`);

    // ════════════════ RESULT ════════════════
    console.log(`\n╔══════════════════════════════════════╗`);
    console.log(`║  ✅${String(pass).padStart(3)}  ❌${String(fail).padStart(3)}  总计${String(pass+fail).padStart(3)}                 ║`);
    if (fail === 0) console.log(`║       🎉 全流程 E2E 通过！          ║`);
    else console.log(`║       ⚠️  有 ${fail} 项未通过            ║`);
    console.log(`╚══════════════════════════════════════╝`);

    if (fail > 0) process.exitCode = 1;
  } catch (err) {
    console.error('❌ 异常:', err.message);
    process.exitCode = 1;
  }
  await browser.close();
}

main();
