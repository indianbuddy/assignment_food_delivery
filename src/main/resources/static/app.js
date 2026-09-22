// Minimal vanilla-JS demo UI for the Food Delivery API. No build step, no
// framework - talks directly to the same /api/** endpoints as any other
// client, with a plain Bearer JWT stored in localStorage. See README.md for
// why this exists at all (it's explicitly out of scope for the assignment).

const APP = document.getElementById('app');
const WHO = document.getElementById('whoBox');

const state = {
  get token() { return localStorage.getItem('fd_token'); },
  set token(v) { v ? localStorage.setItem('fd_token', v) : localStorage.removeItem('fd_token'); },
  get role() { return localStorage.getItem('fd_role'); },
  set role(v) { v ? localStorage.setItem('fd_role', v) : localStorage.removeItem('fd_role'); },
  get userId() { return localStorage.getItem('fd_userId'); },
  set userId(v) { v ? localStorage.setItem('fd_userId', v) : localStorage.removeItem('fd_userId'); },
  get email() { return localStorage.getItem('fd_email'); },
  set email(v) { v ? localStorage.setItem('fd_email', v) : localStorage.removeItem('fd_email'); },
  tab: null,
};

function clearSession() {
  state.token = null; state.role = null; state.userId = null; state.email = null;
  state.tab = null;
}

function toast(message, isError) {
  document.querySelectorAll('.toast').forEach(t => t.remove());
  const el = document.createElement('div');
  el.className = 'toast' + (isError ? ' error' : '');
  el.textContent = message;
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 3500);
}

async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth && state.token) headers['Authorization'] = 'Bearer ' + state.token;
  const res = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  const data = text ? (() => { try { return JSON.parse(text); } catch { return text; } })() : null;
  if (!res.ok) {
    const msg = (data && (data.message || data.error)) || `HTTP ${res.status}`;
    if (res.status === 401) { clearSession(); render(); }
    throw new Error(msg);
  }
  return data;
}

function statusPill(status) {
  return `<span class="status-pill status-${status}">${status.replace(/_/g, ' ')}</span>`;
}

function el(html) {
  const t = document.createElement('template');
  t.innerHTML = html.trim();
  return t.content.firstChild;
}

// ---------------------------------------------------------------- Auth ----

function renderAuth() {
  WHO.innerHTML = '';
  const wrap = el(`
    <div class="auth-wrap">
      <div class="tabs">
        <button data-tab="login" class="active">Log in</button>
        <button data-tab="register">Register</button>
      </div>
      <div class="card" id="authCard"></div>
    </div>
  `);
  APP.innerHTML = '';
  APP.appendChild(wrap);

  const authCard = wrap.querySelector('#authCard');
  const tabs = wrap.querySelectorAll('.tabs button');
  tabs.forEach(btn => btn.addEventListener('click', () => {
    tabs.forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    renderAuthForm(btn.dataset.tab, authCard);
  }));
  renderAuthForm('login', authCard);
}

function renderAuthForm(mode, container) {
  if (mode === 'login') {
    container.innerHTML = `
      <h3>Log in</h3>
      <form class="inline" id="loginForm" style="flex-direction:column;align-items:stretch;">
        <label>Email <input type="email" name="email" required placeholder="owner1@qryde.com"></label>
        <label>Password <input type="password" name="password" required></label>
        <button class="btn" type="submit">Log in</button>
      </form>`;
    container.querySelector('#loginForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      try {
        const resp = await api('/api/auth/login', { auth: false, method: 'POST',
          body: { email: f.get('email'), password: f.get('password') } });
        applySession(resp);
      } catch (err) { toast(err.message, true); }
    });
  } else {
    container.innerHTML = `
      <h3>Register</h3>
      <form class="inline" id="registerForm" style="flex-direction:column;align-items:stretch;">
        <label>Full name <input name="fullName" required></label>
        <label>Email <input type="email" name="email" required></label>
        <label>Password <input type="password" name="password" required minlength="8"></label>
        <label>Phone <input name="phone" required value="+10000000"></label>
        <label>Role
          <select name="role">
            <option value="CUSTOMER">Customer</option>
            <option value="RESTAURANT_OWNER">Restaurant owner</option>
            <option value="DELIVERY_PARTNER">Delivery partner</option>
          </select>
        </label>
        <button class="btn" type="submit">Create account</button>
      </form>
      <p class="empty">Admin accounts can't self-register (see README) - they're bootstrapped separately.</p>`;
    container.querySelector('#registerForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      try {
        const resp = await api('/api/auth/register', { auth: false, method: 'POST',
          body: { fullName: f.get('fullName'), email: f.get('email'), password: f.get('password'),
                  phone: f.get('phone'), role: f.get('role') } });
        applySession(resp);
      } catch (err) { toast(err.message, true); }
    });
  }
}

function applySession(resp) {
  state.token = resp.token;
  state.role = resp.role;
  state.userId = resp.userId;
  state.email = resp.email;
  toast(`Welcome, ${resp.email}`);
  render();
}

// -------------------------------------------------------------- Shell ----

function renderShell() {
  WHO.innerHTML = `
    <span class="role-pill">${state.role}</span>
    <span>${state.email}</span>
    <button class="linklike" id="logoutBtn">Log out</button>`;
  WHO.querySelector('#logoutBtn').addEventListener('click', () => { clearSession(); render(); });

  const tabsByRole = {
    CUSTOMER: [['browse', 'Browse & order'], ['orders', 'My orders']],
    RESTAURANT_OWNER: [['restaurants', 'My restaurants'], ['orders', 'Orders'], ['ratings', 'Ratings']],
    DELIVERY_PARTNER: [['status', 'My status'], ['available', 'Available orders'], ['mine', 'My deliveries']],
    ADMIN: [['cities', 'Cities'], ['restaurants', 'Restaurants'], ['partners', 'Delivery partners']],
  };
  const tabs = tabsByRole[state.role] || [];
  if (!state.tab) state.tab = tabs[0][0];

  const shell = el(`<div><div class="tabs" id="roleTabs"></div><div id="tabBody"></div></div>`);
  APP.innerHTML = '';
  APP.appendChild(shell);

  const tabBar = shell.querySelector('#roleTabs');
  tabs.forEach(([key, label]) => {
    const b = el(`<button>${label}</button>`);
    if (key === state.tab) b.classList.add('active');
    b.addEventListener('click', () => { state.tab = key; renderShell(); });
    tabBar.appendChild(b);
  });

  const body = shell.querySelector('#tabBody');
  const renderers = {
    CUSTOMER: { browse: renderCustomerBrowse, orders: renderCustomerOrders },
    RESTAURANT_OWNER: { restaurants: renderOwnerRestaurants, orders: renderOwnerOrders, ratings: renderOwnerRatings },
    DELIVERY_PARTNER: { status: renderPartnerStatus, available: renderPartnerAvailable, mine: renderPartnerMine },
    ADMIN: { cities: renderAdminCities, restaurants: renderAdminRestaurants, partners: renderAdminPartners },
  };
  (renderers[state.role][state.tab] || (() => { body.innerHTML = ''; }))(body);
}

// ----------------------------------------------------------- Customer ----

async function renderCustomerBrowse(container) {
  container.innerHTML = `<div class="card"><h3>Cities</h3><div id="cityList">Loading...</div></div>`;
  const cities = await api('/api/cities', { auth: false }).catch(err => { toast(err.message, true); return []; });
  const cityList = container.querySelector('#cityList');
  if (!cities.length) { cityList.innerHTML = `<p class="empty">No active cities yet - ask an admin to create one.</p>`; return; }
  cityList.innerHTML = '';
  const select = el(`<label>Choose a city
    <select id="citySelect">${cities.map(c => `<option value="${c.id}">${c.name}</option>`).join('')}</select>
  </label>`);
  cityList.appendChild(select);
  const restaurantsBox = el(`<div id="restaurantsBox" style="margin-top:14px;"></div>`);
  cityList.appendChild(restaurantsBox);

  async function loadRestaurants() {
    const cityId = select.querySelector('select').value;
    const restaurants = await api(`/api/restaurants?cityId=${cityId}`, { auth: false });
    restaurantsBox.innerHTML = restaurants.length ? '' : '<p class="empty">No restaurants in this city yet.</p>';
    for (const r of restaurants) {
      const card = el(`<div class="card"><h3>${r.name} <span class="muted">${r.address}</span></h3><div class="menu-${r.id}"></div></div>`);
      restaurantsBox.appendChild(card);
      loadMenu(r.id, card.querySelector(`.menu-${r.id}`), r.name);
    }
  }
  select.querySelector('select').addEventListener('change', loadRestaurants);
  loadRestaurants();
  renderCart();
}

const cart = { restaurantId: null, restaurantName: null, items: {} };

async function loadMenu(restaurantId, container, restaurantName) {
  const items = await api(`/api/restaurants/${restaurantId}/menu-items`, { auth: false });
  if (!items.length) { container.innerHTML = '<p class="empty">No menu items yet.</p>'; return; }
  container.innerHTML = '';
  items.forEach(item => {
    const row = el(`
      <div class="item-row">
        <div><div class="name">${item.name}</div><div class="sub">$${item.price.toFixed(2)} &middot; ${item.stockQuantity} in stock</div></div>
        <button class="btn secondary" ${item.stockQuantity < 1 ? 'disabled' : ''}>Add to cart</button>
      </div>`);
    row.querySelector('button').addEventListener('click', () => {
      if (cart.restaurantId && cart.restaurantId !== restaurantId) {
        if (!confirm(`Your cart has items from ${cart.restaurantName}. Clear it and start a new order from ${restaurantName}?`)) return;
        cart.items = {};
      }
      cart.restaurantId = restaurantId;
      cart.restaurantName = restaurantName;
      cart.items[item.id] = { name: item.name, price: item.price, qty: (cart.items[item.id]?.qty || 0) + 1 };
      renderCart();
      toast(`Added ${item.name}`);
    });
    container.appendChild(row);
  });
}

function renderCart() {
  document.querySelectorAll('.cart-panel').forEach(n => n.remove());
  const ids = Object.keys(cart.items);
  if (!ids.length) return;
  const panel = el(`<div class="card cart-panel" style="position:sticky;bottom:12px;"><h3>Cart &mdash; ${cart.restaurantName}</h3><div id="cartLines"></div><button class="btn" id="placeOrderBtn" style="margin-top:10px;">Place order</button></div>`);
  const lines = panel.querySelector('#cartLines');
  let total = 0;
  ids.forEach(id => {
    const it = cart.items[id];
    total += it.price * it.qty;
    lines.appendChild(el(`<div class="cart-line"><span>${it.qty} &times; ${it.name}</span><span>$${(it.price * it.qty).toFixed(2)}</span></div>`));
  });
  lines.appendChild(el(`<div class="cart-line" style="font-weight:700;border-top:1px solid var(--border);padding-top:6px;"><span>Total</span><span>$${total.toFixed(2)}</span></div>`));
  panel.querySelector('#placeOrderBtn').addEventListener('click', async () => {
    try {
      const body = {
        restaurantId: cart.restaurantId,
        items: ids.map(id => ({ menuItemId: Number(id), quantity: cart.items[id].qty })),
      };
      const order = await api('/api/orders', { method: 'POST', body });
      cart.items = {}; cart.restaurantId = null;
      renderCart();
      toast(`Order #${order.id} placed!`);
      state.tab = 'orders';
      renderShell();
    } catch (err) { toast(err.message, true); }
  });
  document.getElementById('app').appendChild(panel);
}

async function renderCustomerOrders(container) {
  container.innerHTML = '<div class="card">Loading...</div>';
  const orders = await api('/api/orders/mine').catch(err => { toast(err.message, true); return []; });
  if (!orders.length) { container.innerHTML = '<div class="card"><p class="empty">No orders yet.</p></div>'; return; }
  container.innerHTML = '';
  orders.forEach(o => container.appendChild(orderCard(o, { customer: true })));
}

function orderCard(o, opts = {}) {
  const card = el(`
    <div class="card">
      <h3>Order #${o.id} &mdash; ${o.restaurantName} ${statusPill(o.status)}</h3>
      <div>${o.items.map(i => `<div class="item-row"><span>${i.quantity} &times; ${i.menuItemName}</span><span>$${(i.priceAtOrder * i.quantity).toFixed(2)}</span></div>`).join('')}</div>
      <div class="item-row" style="font-weight:700;"><span>Total</span><span>$${o.totalAmount.toFixed(2)}</span></div>
      <div id="actions-${o.id}" style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap;"></div>
    </div>`);
  const actions = card.querySelector(`#actions-${o.id}`);

  if (opts.customer) {
    if (o.status === 'PLACED' || o.status === 'ACCEPTED') {
      actions.appendChild(actionBtn('Cancel order', 'danger', () => api(`/api/orders/${o.id}/cancel`, { method: 'POST' })));
    }
    if (o.status === 'DELIVERED') {
      actions.appendChild(ratingForm(o.id));
    }
  }
  if (opts.owner) {
    if (o.status === 'PLACED') {
      actions.appendChild(actionBtn('Accept', '', () => api(`/api/owner/orders/${o.id}/accept`, { method: 'POST' })));
      actions.appendChild(actionBtn('Reject', 'danger', () => api(`/api/owner/orders/${o.id}/reject`, { method: 'POST' })));
    }
    if (o.status === 'ACCEPTED') {
      actions.appendChild(actionBtn('Mark preparing', '', () => api(`/api/owner/orders/${o.id}/preparing`, { method: 'POST' })));
    }
  }
  if (opts.partner) {
    if (o.status === 'PREPARING') {
      actions.appendChild(actionBtn('Out for delivery', '', () => api(`/api/delivery/orders/${o.id}/out-for-delivery`, { method: 'POST' })));
    }
    if (o.status === 'OUT_FOR_DELIVERY') {
      actions.appendChild(actionBtn('Mark delivered', '', () => api(`/api/delivery/orders/${o.id}/delivered`, { method: 'POST' })));
    }
    if (!o.deliveryPartnerId && (o.status === 'ACCEPTED' || o.status === 'PREPARING')) {
      actions.appendChild(actionBtn('Accept this delivery', '', () => api(`/api/delivery/orders/${o.id}/accept`, { method: 'POST' })));
    }
  }
  return card;

  function actionBtn(label, cls, fn) {
    const b = el(`<button class="btn ${cls}">${label}</button>`);
    b.addEventListener('click', async () => {
      try { await fn(); toast(`${label} done`); renderShell(); }
      catch (err) { toast(err.message, true); }
    });
    return b;
  }
}

function ratingForm(orderId) {
  const wrap = el(`
    <form class="inline" style="width:100%;">
      <label>Restaurant (1-5) <input type="number" min="1" max="5" name="restaurantRating" value="5" style="width:60px;"></label>
      <label>Delivery (1-5) <input type="number" min="1" max="5" name="deliveryRating" value="5" style="width:60px;"></label>
      <label>Comment <input name="comment" placeholder="optional"></label>
      <button class="btn secondary" type="submit">Rate order</button>
    </form>`);
  wrap.addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    try {
      await api(`/api/orders/${orderId}/rating`, { method: 'POST', body: {
        restaurantRating: Number(f.get('restaurantRating')),
        deliveryRating: Number(f.get('deliveryRating')),
        comment: f.get('comment') || null,
      }});
      toast('Thanks for rating!');
      renderShell();
    } catch (err) { toast(err.message, true); }
  });
  return wrap;
}

// ------------------------------------------------------------- Owner ----

async function renderOwnerRestaurants(container) {
  container.innerHTML = '<div class="card">Loading...</div>';
  const restaurants = await api('/api/owner/restaurants').catch(err => { toast(err.message, true); return []; });
  if (!restaurants.length) { container.innerHTML = '<div class="card"><p class="empty">No restaurants yet - ask an admin to create one and assign it to your account (user id ' + state.userId + ').</p></div>'; return; }
  container.innerHTML = '';
  for (const r of restaurants) {
    const card = el(`
      <div class="card">
        <h3>${r.name} <span class="muted">${r.address}</span></h3>
        <div id="menu-${r.id}"></div>
        <form class="inline" id="add-${r.id}" style="margin-top:12px;">
          <label>Name <input name="name" required></label>
          <label>Description <input name="description"></label>
          <label>Price <input type="number" step="0.01" name="price" required style="width:80px;"></label>
          <label>Stock <input type="number" name="stockQuantity" required style="width:70px;"></label>
          <button class="btn" type="submit">Add item</button>
        </form>
      </div>`);
    container.appendChild(card);
    loadOwnerMenu(r.id, card.querySelector(`#menu-${r.id}`));
    card.querySelector(`#add-${r.id}`).addEventListener('submit', async (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      try {
        await api(`/api/owner/restaurants/${r.id}/menu-items`, { method: 'POST', body: {
          name: f.get('name'), description: f.get('description'),
          price: Number(f.get('price')), stockQuantity: Number(f.get('stockQuantity')),
        }});
        toast('Item added'); renderShell();
      } catch (err) { toast(err.message, true); }
    });
  }
}

async function loadOwnerMenu(restaurantId, container) {
  const items = await api(`/api/owner/restaurants/${restaurantId}/menu-items`);
  if (!items.length) { container.innerHTML = '<p class="empty">No menu items yet.</p>'; return; }
  container.innerHTML = '';
  items.forEach(item => {
    const row = el(`
      <div class="item-row">
        <div><div class="name">${item.name}</div><div class="sub">$${item.price.toFixed(2)} &middot; ${item.stockQuantity} in stock &middot; ${item.available ? 'available' : 'hidden'}</div></div>
        <div style="display:flex;gap:6px;">
          <button class="btn secondary" data-act="toggle">${item.available ? 'Hide' : 'Show'}</button>
          <button class="btn secondary" data-act="plus">+5 stock</button>
        </div>`);
    row.querySelector('[data-act="toggle"]').addEventListener('click', async () => {
      await api(`/api/owner/menu-items/${item.id}/availability?available=${!item.available}`, { method: 'PATCH' });
      renderShell();
    });
    row.querySelector('[data-act="plus"]').addEventListener('click', async () => {
      await api(`/api/owner/menu-items/${item.id}/stock`, { method: 'PATCH', body: { delta: 5 } });
      renderShell();
    });
    container.appendChild(row);
  });
}

async function renderOwnerOrders(container) {
  container.innerHTML = '<div class="card">Loading...</div>';
  const restaurants = await api('/api/owner/restaurants').catch(() => []);
  if (!restaurants.length) { container.innerHTML = '<div class="card"><p class="empty">No restaurants assigned to you yet.</p></div>'; return; }
  container.innerHTML = '';
  for (const r of restaurants) {
    const orders = await api(`/api/owner/restaurants/${r.id}/orders`).catch(err => { toast(err.message, true); return []; });
    const section = el(`<div><h3 style="margin:18px 0 8px;">${r.name}</h3></div>`);
    container.appendChild(section);
    if (!orders.length) { container.appendChild(el('<p class="empty">No orders yet.</p>')); continue; }
    orders.forEach(o => container.appendChild(orderCard(o, { owner: true })));
  }
}

async function renderOwnerRatings(container) {
  container.innerHTML = '<div class="card">Loading...</div>';
  const restaurants = await api('/api/owner/restaurants').catch(() => []);
  container.innerHTML = '';
  for (const r of restaurants) {
    const ratings = await api(`/api/owner/restaurants/${r.id}/ratings`).catch(() => []);
    const card = el(`<div class="card"><h3>${r.name}</h3></div>`);
    if (!ratings.length) card.appendChild(el('<p class="empty">No ratings yet.</p>'));
    ratings.forEach(rt => card.appendChild(el(`<div class="item-row"><span>Order #${rt.orderId} &mdash; food ${rt.restaurantRating}/5, delivery ${rt.deliveryRating}/5</span><span class="sub">${rt.comment || ''}</span></div>`)));
    container.appendChild(card);
  }
}

// ---------------------------------------------------------- Partner ----

async function renderPartnerStatus(container) {
  container.innerHTML = `
    <div class="card">
      <h3>My status</h3>
      <div class="tabs">
        <button data-s="AVAILABLE">Available</button>
        <button data-s="BUSY">Busy</button>
        <button data-s="OFFLINE">Offline</button>
      </div>
      <p class="empty">You must be registered as a delivery partner by an admin before this works (links your account to a city).</p>
    </div>`;
  container.querySelectorAll('[data-s]').forEach(b => b.addEventListener('click', async () => {
    try {
      await api('/api/delivery/me/status', { method: 'PATCH', body: { status: b.dataset.s } });
      toast(`Status set to ${b.dataset.s}`);
    } catch (err) { toast(err.message, true); }
  }));
}

async function renderPartnerAvailable(container) {
  container.innerHTML = '<div class="card">Loading...</div>';
  const orders = await api('/api/delivery/available-orders').catch(err => { toast(err.message, true); return []; });
  if (!orders.length) { container.innerHTML = '<div class="card"><p class="empty">No unclaimed orders in your city right now.</p></div>'; return; }
  container.innerHTML = '';
  orders.forEach(o => container.appendChild(orderCard(o, { partner: true })));
}

async function renderPartnerMine(container) {
  container.innerHTML = '<div class="card">Loading...</div>';
  const orders = await api('/api/delivery/orders/mine').catch(err => { toast(err.message, true); return []; });
  if (!orders.length) { container.innerHTML = '<div class="card"><p class="empty">No assignments yet.</p></div>'; return; }
  container.innerHTML = '';
  orders.forEach(o => container.appendChild(orderCard(o, { partner: true })));
}

// ------------------------------------------------------------- Admin ----

async function renderAdminCities(container) {
  container.innerHTML = `
    <div class="card">
      <h3>Create city</h3>
      <form class="inline" id="cityForm"><label>Name <input name="name" required></label><button class="btn" type="submit">Create</button></form>
    </div>
    <div class="card"><h3>All cities</h3><div id="cityList">Loading...</div></div>`;
  container.querySelector('#cityForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    try { await api('/api/admin/cities', { method: 'POST', body: { name: new FormData(e.target).get('name') } }); toast('City created'); renderShell(); }
    catch (err) { toast(err.message, true); }
  });
  const cities = await api('/api/admin/cities').catch(err => { toast(err.message, true); return []; });
  const list = container.querySelector('#cityList');
  list.innerHTML = cities.length ? '' : '<p class="empty">No cities yet.</p>';
  cities.forEach(c => {
    const row = el(`<div class="item-row"><span>${c.name} ${statusPill(c.active ? 'DELIVERED' : 'REJECTED')} <span class="sub">id ${c.id}</span></span><button class="btn secondary">${c.active ? 'Deactivate' : 'Activate'}</button></div>`);
    row.querySelector('button').addEventListener('click', async () => {
      await api(`/api/admin/cities/${c.id}/active?active=${!c.active}`, { method: 'PATCH' });
      renderShell();
    });
    list.appendChild(row);
  });
}

async function renderAdminRestaurants(container) {
  const cities = await api('/api/admin/cities').catch(() => []);
  container.innerHTML = `
    <div class="card">
      <h3>Create restaurant</h3>
      <form class="inline" id="restForm">
        <label>Name <input name="name" required></label>
        <label>Address <input name="address" required></label>
        <label>City <select name="cityId">${cities.map(c => `<option value="${c.id}">${c.name}</option>`).join('')}</select></label>
        <label>Owner user id <input type="number" name="ownerId" required title="A registered RESTAURANT_OWNER account's user id"></label>
        <button class="btn" type="submit">Create</button>
      </form>
      <p class="empty">The owner must already have registered a RESTAURANT_OWNER account - their user id is shown on their own login (or ask them).</p>
    </div>
    <div class="card"><h3>By city</h3><div id="restList"></div></div>`;
  container.querySelector('#restForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    try {
      await api('/api/admin/restaurants', { method: 'POST', body: {
        name: f.get('name'), address: f.get('address'), cityId: Number(f.get('cityId')), ownerId: Number(f.get('ownerId')),
      }});
      toast('Restaurant created'); renderShell();
    } catch (err) { toast(err.message, true); }
  });
  const list = container.querySelector('#restList');
  for (const c of cities) {
    const restaurants = await api(`/api/restaurants?cityId=${c.id}`, { auth: false }).catch(() => []);
    if (!restaurants.length) continue;
    list.appendChild(el(`<h3 style="margin:10px 0 4px;">${c.name}</h3>`));
    restaurants.forEach(r => list.appendChild(el(`<div class="item-row"><span>${r.name} <span class="sub">${r.address}</span></span>${statusPill(r.active ? 'DELIVERED' : 'REJECTED')}</div>`)));
  }
}

async function renderAdminPartners(container) {
  const cities = await api('/api/admin/cities').catch(() => []);
  container.innerHTML = `
    <div class="card">
      <h3>Register a delivery partner</h3>
      <form class="inline" id="partnerForm">
        <label>User id <input type="number" name="userId" required title="A registered DELIVERY_PARTNER account's user id"></label>
        <label>City <select name="cityId">${cities.map(c => `<option value="${c.id}">${c.name}</option>`).join('')}</select></label>
        <button class="btn" type="submit">Register</button>
      </form>
      <p class="empty">The person must already have a DELIVERY_PARTNER account (self-registered). This links that account to a city so they can accept orders there.</p>
    </div>`;
  container.querySelector('#partnerForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    try {
      await api('/api/admin/delivery-partners', { method: 'POST', body: { userId: Number(f.get('userId')), cityId: Number(f.get('cityId')) } });
      toast('Delivery partner registered');
      e.target.reset();
    } catch (err) { toast(err.message, true); }
  });
}

// -------------------------------------------------------------- Root ----

function render() {
  if (!state.token) { renderAuth(); return; }
  renderShell();
}

render();
