const spec = window.GYM_SPEC, panels = window.GYM_PANELS;
const box = document.getElementById('screen');
let role = 'MANAGER', current = 'UI-01';

const ROUTE_MAP = {
    'UI-01': '/login · FR-01',
    'UI-02': '/members · FR-03, FR-04, FR-05',
    'UI-03': '/plans · FR-06, FR-07',
    'UI-04': '/members/{id}/sales/new · FR-08, FR-09, FR-10',
    'UI-05': '/receipts/{id} · FR-12',
    'UI-06': '/visits · FR-13, FR-14, FR-15',
    'UI-07': '/trainers · FR-17, FR-18',
    'UI-08': '/pt-bookings · FR-19, FR-20, FR-21, FR-22, FR-23',
    'UI-09': '/reports · FR-24',
    'UI-10': '/users · FR-16, FR-02',
    'UI-11': '/equipment · FR-25 (Should)'
};

const NAV_GROUPS = [
    { label: 'Quầy tiếp nhận', ids: ['UI-02', 'UI-03', 'UI-04', 'UI-05', 'UI-06'] },
    { label: 'PT & Lịch tập', ids: ['UI-07', 'UI-08'] },
    { label: 'Quản trị', ids: ['UI-09', 'UI-10'] },
    { label: 'Cơ sở vật chất', ids: ['UI-11'] },
    { label: 'Hệ thống', ids: ['UI-01'] }
];

const esc = x => String(x ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[c]));

function message(s) {
    document.getElementById('message').textContent = s;
}

function setProtoTheme(mode) {
    document.documentElement.setAttribute('data-theme', mode);
    try { localStorage.setItem('gym-theme', mode); } catch (e) {}
    syncProtoThemeButtons(mode);
}

function syncProtoThemeButtons(mode) {
    const active = mode || document.documentElement.getAttribute('data-theme') || 'light';
    const l = document.getElementById('btnProtoLight');
    const d = document.getElementById('btnProtoDark');
    if (l && d) {
        l.classList.toggle('is-active', active !== 'dark');
        d.classList.toggle('is-active', active === 'dark');
        l.setAttribute('aria-pressed', String(active !== 'dark'));
        d.setAttribute('aria-pressed', String(active === 'dark'));
    }
}

function allowed(id) {
    return role === 'MANAGER'
        || (role === 'TRAINER' ? ['UI-01', 'UI-07', 'UI-08', 'UI-11'].includes(id) : !['UI-09', 'UI-10'].includes(id));
}

function buttonAllowed(label) {
    if (role === 'MANAGER') return true;
    if (['Lưu gói', 'Ngừng bán', 'Ngừng phục vụ', 'Lưu hồ sơ PT', 'Ngừng PT', 'Lưu thiết bị', 'Đang bảo trì', 'Khôi phục sử dụng'].includes(label)) return false;
    if (role === 'RECEPTIONIST' && ['Lưu khung giờ', 'Xóa khung giờ', 'Hoàn thành', 'Vắng mặt'].includes(label)) return false;
    if (role === 'TRAINER' && ['Đặt buổi PT 60 phút', 'Hủy lịch trước giờ'].includes(label)) return false;
    return true;
}

function show(id) {
    if (!allowed(id)) {
        message('Vai trò mẫu hiện tại không có quyền mở màn hình này.');
        return;
    }
    current = id;
    message('');
    const roleNames = { MANAGER: 'Quản lý mẫu · Quản lý', RECEPTIONIST: 'Lễ tân mẫu · Lễ tân', TRAINER: 'PT mẫu Minh · Huấn luyện viên' };
    document.getElementById('identity').textContent = roleNames[role] || role;

    const navHtml = NAV_GROUPS.map(g => {
        const btns = g.ids.filter(allowed).map(sid => {
            const s = spec.screens.find(x => x.id === sid);
            if (!s) return '';
            return `<button type="button" data-screen="${sid}" aria-current="${sid === id ? 'page' : 'false'}">${sid} · ${esc(s.name)}</button>`;
        }).join('');
        return btns ? `<div class="nav-sep">${esc(g.label)}</div>${btns}` : '';
    }).join('');
    document.getElementById('navigation').innerHTML = navHtml;

    if (id === 'UI-01') {
        box.innerHTML = `<div class="login card">
            <div class="tag">DMT GYM · ${esc(ROUTE_MAP[id])}</div>
            <h1>Đăng nhập (Mô phỏng)</h1>
            <p class="muted">Chọn vai trò để xem luồng phân quyền của 11 màn hình prototype. Xác thực thật chạy ở ứng dụng Java.</p>
            <form id="login">
                <label for="user">Tên đăng nhập mẫu</label><input id="user" value="manager">
                <label for="password">Mật khẩu mẫu</label><input id="password" type="password" value="demo">
                <label for="role">Vai trò minh họa</label>
                <select id="role"><option>MANAGER</option><option>RECEPTIONIST</option><option>TRAINER</option></select>
                <div class="actions"><button type="submit">Vào màn hình</button></div>
            </form>
        </div>`;
        return;
    }

    const s = spec.screens.find(x => x.id === id), p = panels[id];
    let rows = p.rows;
    if (role === 'TRAINER' && id === 'UI-07') rows = rows.filter(r => r[0].includes('Minh'));
    if (role === 'TRAINER' && id === 'UI-08') rows = rows.filter(r => r[1].includes('Minh'));

    box.innerHTML = `
        <div class="tag">${id} · ${esc(ROUTE_MAP[id] || '')}</div>
        <h1>${esc(s.name)}</h1>
        <p class="muted">${esc(p.note)}</p>
        <div class="card scroll">
            <table>
                <thead><tr>${p.cols.map(x => '<th>' + esc(x) + '</th>').join('')}</tr></thead>
                <tbody>${rows.map(r => '<tr>' + r.map(x => '<td>' + esc(x) + '</td>').join('') + '</tr>').join('')}</tbody>
            </table>
        </div>
        ${p.fields.length ? `<div class="card">
            <h2>Thao tác minh họa (không ghi CSDL)</h2>
            <div class="row">${p.fields.map((f, i) => `<div><label for="f${i}">${esc(f)}</label><input id="f${i}" placeholder="${esc(f)}" ${f.includes('Mật khẩu') ? 'type="password"' : ''}></div>`).join('')}</div>
            <div class="actions">${p.buttons.filter(buttonAllowed).map(b => `<button type="button" class="${b.includes('Hủy') || b.includes('Ngừng') ? 'secondary' : ''}" data-action="${esc(b)}">${esc(b)}</button>`).join('')}</div>
        </div>` : ''}
        <p class="muted">Lưu ý: Đây là màn hình prototype mô phỏng với ngày nền 17/11/2026; mọi thao tác lưu thật thực hiện trên ứng dụng Java DMT Gym.</p>`;
}

document.addEventListener('click', e => {
    const b = e.target.closest('button');
    if (!b) return;
    if (b.dataset.screen) show(b.dataset.screen);
    if (b.dataset.action) {
        if (b.dataset.action === 'Xác nhận nhận đủ tiền mặt') {
            show('UI-05');
            message('Mô phỏng chuyển sang phiếu thu. Trên Java thật, giao dịch ghi nguyên tử memberships + payments.');
        } else {
            message('Mô phỏng hành động "' + b.dataset.action + '" (không ghi DB). ' + panels[current].note);
        }
    }
});

document.addEventListener('submit', e => {
    e.preventDefault();
    if (e.target.id === 'login') {
        role = document.getElementById('role').value;
        show(role === 'TRAINER' ? 'UI-08' : 'UI-02');
    }
});

document.addEventListener('DOMContentLoaded', () => syncProtoThemeButtons());
show('UI-01');
