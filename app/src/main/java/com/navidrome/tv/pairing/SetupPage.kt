package com.navidrome.tv.pairing

/** 內建手機設定頁（依 design/v1/14-mobile-setup）。無外部資源、無 CDN。 */
object SetupPage {
    val html: String = """
<!DOCTYPE html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Navidrome TV · 连接你的电视</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background: #0e1216; color: #f2f6f4; min-height: 100vh;
    font-family: -apple-system, "PingFang TC", "Noto Sans TC", system-ui, sans-serif;
    display: flex; flex-direction: column; align-items: center; padding: 40px 20px 60px;
  }
  .brand { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 20px; margin-bottom: 28px; }
  .brand svg { width: 26px; height: 26px; fill: #a8e8c8; }
  h1 { font-size: 34px; font-weight: 800; margin-bottom: 18px; }
  .badge {
    display: inline-flex; align-items: center; gap: 8px; background: #22352c; color: #a8e8c8;
    border-radius: 999px; padding: 8px 18px; font-size: 15px; font-weight: 600; margin-bottom: 32px;
  }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: #a8e8c8; }
  form { width: 100%; max-width: 420px; }
  label { display: block; font-size: 15px; font-weight: 700; margin: 20px 0 8px; }
  .field { position: relative; }
  input {
    width: 100%; background: #161c22; border: 1.5px solid #2a333c; border-radius: 12px;
    color: #f2f6f4; font-size: 17px; padding: 14px 44px 14px 16px; outline: none;
  }
  input:focus { border-color: #a8e8c8; }
  input::placeholder { color: #5e6b74; }
  .eye {
    position: absolute; right: 12px; top: 50%; transform: translateY(-50%);
    background: none; border: none; color: #98a4ac; font-size: 18px; cursor: pointer; padding: 4px;
  }
  button.submit {
    width: 100%; margin-top: 32px; background: #a8e8c8; color: #122019; border: none;
    border-radius: 14px; font-size: 19px; font-weight: 800; padding: 15px; cursor: pointer;
  }
  button.submit:disabled { opacity: .55; }
  .footer { margin-top: 22px; color: #98a4ac; font-size: 14px; }
  .once { margin-top: 40px; display: flex; align-items: center; gap: 8px; color: #f2f6f4; font-weight: 700; font-size: 16px; }
  .once span.mark { color: #a8e8c8; font-size: 20px; }
  .msg { margin-top: 18px; font-size: 15px; min-height: 22px; text-align: center; }
  .msg.err { color: #f2a4a4; }
  .msg.ok { color: #a8e8c8; font-weight: 700; font-size: 18px; }
  button.sug {
    text-align: left; background: #161c22; border: 1.5px solid #2a333c; border-radius: 10px;
    color: #f2f6f4; font-size: 15px; padding: 10px 14px; cursor: pointer; word-break: break-all;
  }
  button.sug:active { border-color: #a8e8c8; }
</style>
</head>
<body>
  <div class="brand"><svg viewBox="0 0 24 24"><path d="M9 18V6l10-2v10.5a3 3 0 1 1-2-2.83V7.4L11 8.8v9.7a3 3 0 1 1-2-2.83z"/></svg>Navidrome TV</div>
  <h1>连接你的电视</h1>
  <div class="badge"><span class="dot"></span>客廳电视 · 已配对</div>
  <form id="f" onsubmit="return submitForm(event)">
    <div id="sugwrap" style="display:none; margin-bottom: 6px;">
      <div style="font-size:14px; color:#a8e8c8; font-weight:700; margin:12px 0 8px;">发现的音乐服务器（点选即可填入）</div>
      <div id="sug" style="display:flex; flex-direction:column; gap:8px;"></div>
    </div>
    <label for="url">音乐服务器地址</label>
    <input id="url" type="url" inputmode="url" autocomplete="url" placeholder="https://music.example.com" required>
    <label for="user">用户名称</label>
    <input id="user" type="text" autocomplete="username" placeholder="listener" required>
    <label for="pass">密码</label>
    <div class="field">
      <input id="pass" type="password" autocomplete="current-password" placeholder="••••••••" required>
      <button type="button" class="eye" onclick="toggle()">显示</button>
    </div>
    <button class="submit" id="btn" type="submit">连接电视</button>
    <div class="msg" id="msg"></div>
    <div class="footer">设定完成后，电视会自動开启歌曲清单。</div>
  </form>
  <div class="once"><span class="mark">✔</span>只需设定一次。</div>
<script>
var busy = false;
function toggle() {
  var p = document.getElementById('pass');
  p.type = p.type === 'password' ? 'text' : 'password';
}
function setMsg(text, cls) {
  var m = document.getElementById('msg');
  m.textContent = text; m.className = 'msg ' + (cls || '');
}
var ERR = {
  reachability: '电视连不上这个地址，请确认服务器地址与网络。',
  credentials: '账号或密码不正确。',
  tls: '服务器凭证或 TLS 有問題。',
  invalid: '配对已过期，请在电视上重新生成二维码。',
  expired: '配对已过期，请在电视上重新生成二维码。'
};
async function discover() {
  var token = location.hash.length > 1 ? location.hash.slice(1) : '';
  try {
    var r = await fetch('/api/discover', { headers: { 'Authorization': 'Bearer ' + token } });
    var d = await r.json();
    var list = d.servers || [];
    if (!list.length) return;
    var box = document.getElementById('sug');
    box.innerHTML = '';
    list.forEach(function(u) {
      var b = document.createElement('button');
      b.type = 'button'; b.className = 'sug'; b.textContent = u;
      b.onclick = function() { document.getElementById('url').value = u; };
      box.appendChild(b);
    });
    document.getElementById('sugwrap').style.display = 'block';
    if (list.length === 1) document.getElementById('url').value = list[0];
  } catch (e) {}
}
discover();
function submitForm(e) {
  e.preventDefault();
  go();
  return false;
}
async function go() {
  if (busy) return false;
  busy = true;
  var btn = document.getElementById('btn');
  btn.disabled = true; btn.textContent = '正在连接…';
  setMsg('');
  var token = location.hash.length > 1 ? location.hash.slice(1) : '';
  var body = JSON.stringify({
    serverUrl: document.getElementById('url').value.trim(),
    username: document.getElementById('user').value.trim(),
    password: document.getElementById('pass').value
  });
  try {
    var res = await fetch('/api/config', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
      body: body
    });
    var data = null;
    try { data = await res.json(); } catch (e) {}
    if (res.ok && data && data.ok) {
      setMsg('电视已连接，可以关闭此頁。', 'ok');
    } else if (res.status === 401 || res.status === 410) {
      setMsg(ERR.invalid, 'err');
    } else if (data && data.error && ERR[data.error]) {
      setMsg(ERR[data.error], 'err');
    } else {
      setMsg('设定失敗，请检查后重试。', 'err');
    }
  } catch (e) {
    setMsg('無法连上电视，请确认手机与电视在同一个网络。', 'err');
  }
  btn.disabled = false; btn.textContent = '连接电视'; busy = false;
  return false;
}
</script>
</body>
</html>
"""
}
