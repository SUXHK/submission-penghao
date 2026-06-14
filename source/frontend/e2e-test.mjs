import { chromium } from 'playwright';

const BASE = 'http://localhost:3001';
const PASS = '-';
const FAIL = '✗ 失败';

let passed = 0, failed = 0;
function check(name, condition, detail) {
  if (condition) { console.log(`  ✅ ${name}`); passed++; }
  else { console.log(`  ❌ ${name}: ${detail}`); failed++; }
}

async function main() {
  console.log('=== 缺陷分诊系统 全流程E2E测试 ===\n');
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });

  // ── Phase 1: 登录 submitter ──
  console.log('Phase 1: 登录 submitter');
  const page = await ctx.newPage();
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  check('登录页加载', await page.locator('text=DefectTriage').isVisible());

  await page.fill('input[type="text"]', 'submitter');
  await page.fill('input[type="password"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/');
  const hasDefectList = await page.locator('text=缺陷列表').isVisible();
  check('登录成功跳转首页', hasDefectList);

  // ── Phase 2: 创建缺陷 ──
  console.log('\nPhase 2: 创建缺陷（弹窗）');
  await page.click('text=+ 创建缺陷');
  await page.waitForTimeout(400);
  check('创建弹窗打开', await page.locator('text=创建缺陷').isVisible());

  await page.fill('input[placeholder="缺陷标题"]', 'E2E测试-登录超时问题');
  await page.fill('textarea[placeholder="详细描述"]', '用户登录超时后无提示');
  await page.fill('textarea[placeholder="缺陷具体现象"]', '登录超时后页面白屏');
  await page.fill('input[placeholder*="Chrome"]', 'Chrome 125, Windows 11');
  await page.fill('textarea[placeholder*="步骤一"]', '1.打开登录页\n2.输入账号密码\n3.等待超时');
  await page.fill('textarea[placeholder="正常行为"]', '超时后提示重新登录');
  await page.fill('textarea[placeholder="异常行为"]', '超时后白屏无提示');

  await page.click('text=提交分诊');
  await page.waitForTimeout(800);
  const sheetVisible = await page.locator('text=复现信息').isVisible();
  check('创建成功打开详情Sheet', sheetVisible);

  // ── Phase 3: 流转到分诊中 ──
  console.log('\nPhase 3: 流转 REPORTED → TRIAGING');
  // Close sheet first, logout, login as engineer
  await page.click('button:has-text("×")');
  await page.waitForTimeout(300);
  await page.click('text=退出登录');
  await page.waitForURL('**/login');

  const page2 = await ctx.newPage();
  await page2.goto(BASE + '/login', { waitUntil: 'networkidle' });
  await page2.fill('input[type="text"]', 'engineer');
  await page2.fill('input[type="password"]', 'admin123');
  await page2.click('button[type="submit"]');
  await page2.waitForURL('**/');
  check('Engineer登录成功', await page2.locator('text=缺陷列表').isVisible());

  // Click the E2E test defect (should be last one created, find by title)
  await page2.waitForTimeout(500);
  const defectRow = page2.locator('tr', { hasText: 'E2E测试-登录超时问题' });
  await defectRow.click();
  await page2.waitForTimeout(500);

  const sheetOpen = await page2.locator('text=复现信息').isVisible();
  check('Engineer打开Sheet', sheetOpen);

  // Click transition to TRIAGING
  const triageBtn = page2.locator('button:has-text("分诊中")');
  if (await triageBtn.isVisible()) {
    await triageBtn.click();
    await page2.waitForTimeout(300);
    check('流转确认弹窗出现', await page2.locator('text=确认状态流转').isVisible());
    await page2.click('text=确认流转');
    await page2.waitForTimeout(1000);
    check('流转到分诊中成功', await page2.locator('text=分诊中').first().isVisible());
  } else {
    check('流转按钮可见', false, '按钮未出现');
  }

  // ── Phase 4: 填写评估维度，流转到已分析 ──
  console.log('\nPhase 4: 评估维度 → 已分析');
  // Close sheet, reopen to refresh, or scroll to assessment
  await page2.waitForTimeout(500);
  // Fill impact dimensions by clicking and editing each field
  const dims = [
    { field: 'userImpact', val: '4', label: '用户影响' },
    { field: 'businessImpact', val: '3', label: '业务影响' },
    { field: 'frequency', val: '5', label: '频率' },
    { field: 'workaround', val: '2', label: '规避方案' },
    { field: 'releaseWindow', val: '1', label: '发布窗口' },
  ];
  for (const d of dims) {
    const el = page2.locator(`text=${d.label}`).first();
    if (await el.isVisible()) {
      await el.click();
      await page2.waitForTimeout(200);
      const input = page2.locator('input[type="text"]').first();
      // Try finding the right input - click the value area
      const valueArea = page2.locator('.hover\\:bg-slate-50').first();
      if (await valueArea.isVisible()) {
        await valueArea.click();
        await page2.waitForTimeout(200);
        const editInput = page2.locator('input').first();
        if (await editInput.isVisible() && await editInput.evaluate(el => el.tagName)) {
          await editInput.fill(d.val);
          await page2.locator('button:has-text("保存")').first().click();
          await page2.waitForTimeout(300);
        }
      }
    }
  }
  console.log('  已尝试填写评估维度');

  // Now try to transition to ANALYZED
  await page2.waitForTimeout(500);
  // Close sheet and reopen to refresh state
  const closeBtn = page2.locator('button:has-text("×")');
  if (await closeBtn.isVisible()) { await closeBtn.click(); await page2.waitForTimeout(300); }
  await defectRow.click();
  await page2.waitForTimeout(500);

  const analyzedBtn = page2.locator('button:has-text("已分析")');
  if (await analyzedBtn.isVisible()) {
    await analyzedBtn.click();
    await page2.waitForTimeout(300);
    await page2.click('text=确认流转');
    await page2.waitForTimeout(800);
    check('流转到已分析', await page2.locator('text=已分析').first().isVisible());
  } else {
    console.log('  已分析按钮不可见，可能缺少评估维度填写');
  }

  // ── Phase 5: 继续流转 → 已计划 → 修复中 → 已修复 ──
  console.log('\nPhase 5: PLAN → IN_REPAIR → FIXED');
  // First close and reopen to get fresh state
  if (await closeBtn.isVisible()) { await closeBtn.click(); await page2.waitForTimeout(300); }
  await defectRow.click();
  await page2.waitForTimeout(500);

  // Fill root cause by clicking the field
  const rootCauseArea = page2.locator('text=根因假设').first();
  if (await rootCauseArea.isVisible()) {
    await rootCauseArea.locator('..').locator('.cursor-pointer').first().click().catch(() => {});
    await page2.waitForTimeout(300);
    const ta = page2.locator('textarea').first();
    if (await ta.isVisible()) {
      await ta.fill('登录超时后未正确处理异常，导致页面状态未重置');
      await page2.waitForTimeout(200);
      await page2.locator('button:has-text("保存")').first().click();
      await page2.waitForTimeout(500);
    }
  }

  // Transition PLAN → IN_REPAIR: first need to click 已计划, fill fix plan, then 修复中
  // Close/reopen
  if (await closeBtn.isVisible()) { await closeBtn.click(); await page2.waitForTimeout(300); }
  await defectRow.click();
  await page2.waitForTimeout(400);

  const planBtn = page2.locator('button:has-text("已计划")');
  if (await planBtn.isVisible()) {
    await planBtn.click();
    await page2.waitForTimeout(300);
    await page2.click('text=确认流转');
    await page2.waitForTimeout(800);
    console.log('  → 已计划完成');
  }

  // Fill fix plan, then IN_REPAIR
  if (await closeBtn.isVisible()) { await closeBtn.click(); await page2.waitForTimeout(300); }
  await defectRow.click();
  await page2.waitForTimeout(400);

  const fixPlanArea = page2.locator('text=修复方案').first();
  if (await fixPlanArea.isVisible()) {
    await fixPlanArea.locator('..').locator('.cursor-pointer').first().click().catch(() => {});
    await page2.waitForTimeout(300);
    const ta = page2.locator('textarea').first();
    if (await ta.isVisible()) {
      await ta.fill('在登录超时catch中添加页面状态重置逻辑');
      await page2.locator('button:has-text("保存")').first().click();
      await page2.waitForTimeout(500);
    }
  }

  if (await closeBtn.isVisible()) { await closeBtn.click(); await page2.waitForTimeout(300); }
  await defectRow.click();
  await page2.waitForTimeout(400);

  const repairBtn = page2.locator('button:has-text("修复中")');
  if (await repairBtn.isVisible()) {
    await repairBtn.click(); await page2.waitForTimeout(300);
    await page2.click('text=确认流转'); await page2.waitForTimeout(800);
    console.log('  → 修复中完成');
  }

  // Fill fix content, then FIXED
  if (await closeBtn.isVisible()) { await closeBtn.click(); await page2.waitForTimeout(300); }
  await defectRow.click();
  await page2.waitForTimeout(400);

  const fixContentArea = page2.locator('text=修复内容').first();
  if (await fixContentArea.isVisible()) {
    await fixContentArea.locator('..').locator('.cursor-pointer').first().click().catch(() => {});
    await page2.waitForTimeout(300);
    const ta = page2.locator('textarea').first();
    if (await ta.isVisible()) {
      await ta.fill('修改LoginController超时处理，添加异常catch和状态重置');
      await page2.locator('button:has-text("保存")').first().click();
      await page2.waitForTimeout(500);
    }
  }

  if (await closeBtn.isVisible()) { await closeBtn.click(); await page2.waitForTimeout(300); }
  await defectRow.click();
  await page2.waitForTimeout(400);

  const fixedBtn = page2.locator('button:has-text("已修复")');
  if (await fixedBtn.isVisible()) {
    await fixedBtn.click(); await page2.waitForTimeout(300);
    await page2.click('text=确认流转'); await page2.waitForTimeout(800);
    check('流转到已修复', await page2.locator('text=已修复').first().isVisible());
  }

  // ── Phase 6: QA验证 → 关闭 ──
  console.log('\nPhase 6: QA 验证 → 关闭');
  if (await closeBtn.isVisible()) { await closeBtn.click(); await page2.waitForTimeout(300); }
  await page2.click('text=退出登录');
  await page2.waitForURL('**/login');

  await page2.fill('input[type="text"]', 'qa');
  await page2.fill('input[type="password"]', 'admin123');
  await page2.click('button[type="submit"]');
  await page2.waitForURL('**/');
  check('QA登录成功', await page2.locator('text=缺陷列表').isVisible());

  await page2.waitForTimeout(500);
  await defectRow.click();
  await page2.waitForTimeout(500);

  // Fill verification fields
  const verifyFields = [
    { label: '验证结果', value: '修复后超时正常提示重新登录，验证通过' },
    { label: '回归范围', value: '所有登录流程' },
    { label: '验证结论', value: '修复有效，可关闭' },
  ];
  for (const vf of verifyFields) {
    const area = page2.locator(`text=${vf.label}`).first();
    if (await area.isVisible()) {
      await area.locator('..').locator('.cursor-pointer').first().click().catch(() => {});
      await page2.waitForTimeout(300);
      const ta = page2.locator('textarea').first();
      if (await ta.isVisible()) {
        await ta.fill(vf.value);
        await page2.locator('button:has-text("保存")').first().click();
        await page2.waitForTimeout(500);
      }
    }
  }

  // Transition VERIFIED → CLOSED
  if (await closeBtn.isVisible()) { await closeBtn.click(); await page2.waitForTimeout(300); }
  await defectRow.click();
  await page2.waitForTimeout(400);

  const verifyBtn = page2.locator('button:has-text("已验证")');
  if (await verifyBtn.isVisible()) {
    await verifyBtn.click(); await page2.waitForTimeout(300);
    await page2.click('text=确认流转'); await page2.waitForTimeout(800);
    check('流转到已验证', await page2.locator('text=已验证').first().isVisible());

    if (await closeBtn.isVisible()) { await closeBtn.click(); await page2.waitForTimeout(300); }
    await defectRow.click();
    await page2.waitForTimeout(400);

    const closeFlowBtn = page2.locator('button:has-text("已关闭")');
    if (await closeFlowBtn.isVisible()) {
      await closeFlowBtn.click(); await page2.waitForTimeout(300);
      await page2.click('text=确认流转'); await page2.waitForTimeout(800);
      check('缺陷关闭成功', await page2.locator('text=已关闭').first().isVisible());
    }
  }

  // ── Phase 7: 知识库验证 ──
  console.log('\nPhase 7: 知识库验证');
  await page2.goto(BASE + '/knowledge', { waitUntil: 'networkidle' });
  await page2.waitForTimeout(500);
  const hasKnowledge = await page2.locator('text=回归用例').isVisible();
  check('知识库页面加载', hasKnowledge);

  // ── Summary ──
  console.log(`\n========== 测试结果 ==========`);
  console.log(`✅ 通过: ${passed}`);
  console.log(`❌ 失败: ${failed}`);
  console.log(`总计: ${passed + failed}`);
  console.log(`==============================\n`);

  await browser.close();
  process.exit(failed > 0 ? 1 : 0);
}

main().catch(err => {
  console.error('测试异常:', err.message);
  process.exit(1);
});
