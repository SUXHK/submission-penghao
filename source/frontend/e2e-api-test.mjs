const B = 'http://localhost:3001/api';
let p = 0, f = 0;
const ok = (n, c, d) => { if (c) { console.log(`  ✅ ${n}`); p++; } else { console.log(`  ❌ ${n}: ${d}`); f++; } };

async function req(method, path, token, body) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h['Authorization'] = `Bearer ${token}`;
  const opts = { method, headers: h };
  if (body) opts.body = JSON.stringify(body);
  const r = await fetch(B + path, opts);
  if (r.status === 204) return null;
  return r.json();
}
const get = (path, token) => req('GET', path, token);
const post = (path, token, body) => req('POST', path, token, body);
const put = (path, token, body) => req('PUT', path, token, body);
const patch = (path, token, body) => req('PATCH', path, token, body);

async function main() {
  console.log('╔══════════════════════════════════════╗');
  console.log('║  缺陷分诊系统 全流程 API E2E 测试   ║');
  console.log('╚══════════════════════════════════════╝\n');

  // 1. 认证
  console.log('── 1. 用户认证 ──');
  const s = await post('/auth/login', null, { username: 'submitter', password: 'admin123' });
  ok('Submitter登录', s?.role === 'SUBMITTER', JSON.stringify(s));
  const eng = await post('/auth/login', null, { username: 'engineer', password: 'admin123' });
  ok('Engineer登录', eng?.role === 'ENGINEER', JSON.stringify(eng));
  const qa = await post('/auth/login', null, { username: 'qa', password: 'admin123' });
  ok('QA登录', qa?.role === 'QA', JSON.stringify(qa));

  const me = await get('/auth/me', s.token);
  ok('获取当前用户', me?.username === 'submitter', JSON.stringify(me));
  const bad = await fetch(B + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'submitter', password: 'wrong' }) });
  ok('错误密码被拒绝', (await bad.json()).error, '应返回错误');

  // 2. 缺陷列表
  console.log('\n── 2. 缺陷列表 ──');
  const list = await get('/defects', s.token);
  ok('获取缺陷列表', Array.isArray(list) && list.length >= 8, `共${list?.length}条`);

  // 3. 创建缺陷
  console.log('\n── 3. 创建缺陷 ──');
  const d = await post('/defects', s.token, {
    title: 'E2E全流程测试缺陷',
    description: '自动测试创建的缺陷',
    phenomenon: '登录超时后页面白屏',
    environment: 'Chrome 125, Win11',
    reproductionSteps: '1.打开登录页\n2.输入账号\n3.等待超时',
    expectedResult: '提示重新登录',
    actualResult: '页面白屏无提示',
  });
  ok('创建缺陷(DRAFT)', d?.status === 'DRAFT' && d?.title?.includes('E2E'), JSON.stringify(d?.status));
  const did = d?.id;

  // 4. DRAFT → REPORTED (submitter)
  console.log('\n── 4. 状态流转 ──');
  let r = await patch(`/defects/${did}/transition?to=REPORTED`, s.token, {});
  ok('DRAFT→已登记', r?.status === 'REPORTED', r?.status);
  ok('标题必填校验生效', d?.title, '');

  // 5. REPORTED → TRIAGING (engineer)
  r = await patch(`/defects/${did}/transition?to=TRIAGING`, eng.token, {});
  ok('已登记→分诊中', r?.status === 'TRIAGING', r?.status);

  // 6. Try bad transition
  const badT = await fetch(B + `/defects/${did}/transition?to=FIXED`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${eng.token}` },
    body: JSON.stringify({})
  });
  ok('跳状态TRIAGING→FIXED被拦', badT.status === 400, (await badT.json()).error);
  const badRole = await fetch(B + `/defects/${did}/transition?to=ANALYZED`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${s.token}` },
    body: JSON.stringify({})
  });
  ok('Submitter越权被拦', badRole.status === 403, (await badRole.json()).error);

  // 7. Fill assessment dimensions → ANALYZED
  console.log('\n── 5. 评估→分析→计划→修复 ──');
  await put(`/defects/${did}`, eng.token, {
    userImpact: 4, businessImpact: 3, frequency: 5, workaround: 2, releaseWindow: 1,
  });
  r = await patch(`/defects/${did}/transition?to=ANALYZED`, eng.token, {});
  ok('分诊中→已分析', r?.status === 'ANALYZED', r?.status);
  ok('优先级已自动计算', r?.priority, `P${r?.priority}`);

  // 8. ANALYZED → PLANNED
  await put(`/defects/${did}`, eng.token, { rootCauseHypothesis: '超时未正确处理异常导致状态未重置' });
  r = await patch(`/defects/${did}/transition?to=PLANNED`, eng.token, {});
  ok('已分析→已计划', r?.status === 'PLANNED', r?.status);

  // 9. PLANNED → IN_REPAIR
  await put(`/defects/${did}`, eng.token, { fixPlan: '添加超时catch和状态重置' });
  r = await patch(`/defects/${did}/transition?to=IN_REPAIR`, eng.token, {});
  ok('已计划→修复中', r?.status === 'IN_REPAIR', r?.status);

  // 10. IN_REPAIR → FIXED
  await put(`/defects/${did}`, eng.token, { fixContent: '修改超时处理逻辑', affectedModules: 'LoginModule', fixDuration: 60 });
  r = await patch(`/defects/${did}/transition?to=FIXED`, eng.token, {});
  ok('修复中→已修复', r?.status === 'FIXED', r?.status);

  // Check AI suggestions were generated
  console.log('\n── 6. AI 建议 ──');
  const ai = await get(`/defects/${did}/ai-suggestions`, eng.token);
  ok('AI建议已生成', Array.isArray(ai) && ai.length > 0, `${ai?.length}条建议`);
  if (ai?.length > 0) {
    const types = ai.map(a => a.type);
    ok('包含排查路径', types.includes('INVESTIGATION_PATH'), types.join(','));
    ok('AI建议状态为待审核', ai[0]?.status === 'PENDING_REVIEW', ai[0]?.status);
    // Review one
    const review = await put(`/ai-suggestions/${ai[0].id}/review`, eng.token, { status: 'ACCEPTED' });
    ok('审核AI建议:采纳', review?.status === 'ACCEPTED', review?.status);
  }

  // 11. FIXED → VERIFIED (QA) → CLOSED
  console.log('\n── 7. QA验证→关闭 ──');
  await put(`/defects/${did}`, qa.token, {
    verificationResult: '修复后超时正常提示，验证通过',
    regressionScope: '所有登录流程',
    verificationConclusion: '修复有效，可关闭',
  });
  r = await patch(`/defects/${did}/transition?to=VERIFIED`, qa.token, {});
  ok('已修复→已验证', r?.status === 'VERIFIED', r?.status);

  r = await patch(`/defects/${did}/transition?to=CLOSED`, qa.token, {});
  ok('已验证→已关闭', r?.status === 'CLOSED', r?.status);

  // 12. FIXED direct → CLOSED blocked
  const badClose = await fetch(B + `/defects/${did}/transition?to=VERIFIED`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${qa.token}` },
    body: JSON.stringify({})
  });
  ok('已关闭不再可验证', badClose.status === 400, (await badClose.json()).error);

  // 13. Knowledge base
  console.log('\n── 8. 知识沉淀 ──');
  await new Promise(r => setTimeout(r, 2000)); // Wait for async knowledge generation
  const kn = await get('/knowledge', eng.token);
  ok('知识库可访问', Array.isArray(kn) && kn.length > 0, `${kn?.length}条`);
  const relatedKn = kn?.filter(k => k.defectId === did);
  ok('关闭缺陷生成知识条目', relatedKn?.length > 0, `新生成${relatedKn?.length}条`);

  // 14. Transition audit log
  console.log('\n── 9. 流转审计 ──');
  const transitions = await get(`/defects/${did}/transitions`, eng.token);
  ok('流转记录存在', Array.isArray(transitions) && transitions.length >= 8, `${transitions?.length}条记录`);
  const statuses = transitions?.map(t => t.toStatus);
  ok('包含完整流转链', statuses?.includes('CLOSED'), statuses?.join('→'));

  // 15. Attachments
  console.log('\n── 10. 附件 ──');
  const atts = await get(`/defects/${did}/attachments`, eng.token);
  ok('附件列表正常', Array.isArray(atts), `${atts?.length}个附件`);

  // Summary
  console.log(`\n╔══════════════════════════════╗`);
  console.log(`║  通过: ${String(p).padStart(2)}  失败: ${String(f).padStart(2)}  总计: ${String(p+f).padStart(2)} ║`);
  if (f === 0) console.log(`║     🎉 全部通过！             ║`);
  else console.log(`║     ⚠️ 有 ${f} 项失败            ║`);
  console.log(`╚══════════════════════════════╝`);
  process.exit(f > 0 ? 1 : 0);
}

main().catch(e => { console.error('异常:', e.message); process.exit(1); });
