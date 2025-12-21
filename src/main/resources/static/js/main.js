(() => {
    const state = {
        cart: [],
        editingIndex: null,
        isAdmin: document.body.dataset.role === 'ROLE_ADMIN',
        tables: [],
        selectedTable: null,
        surchargeName: document.body.dataset.surchargeName || 'Phụ thu',
        surchargePercent: Number(document.body.dataset.surchargePercent || 0),
        cashGiven: 0,
        checkoutMode: false
    };

    const CART_STORAGE_PREFIX = 'pos_cart_';

    const productGrid = document.getElementById('productGrid');
    const productSearch = document.getElementById('productSearch');
    const categoryButtons = document.querySelectorAll('.category-btn');
    const template = document.getElementById('cartItemTemplate');
    const notifyKitchenBtn = document.getElementById('notifyKitchenBtn');
    const notifyKitchenBtnMobile = document.getElementById('notifyKitchenBtnMobile');
    const notifyKitchenButtons = [notifyKitchenBtn, notifyKitchenBtnMobile].filter(Boolean);
    if (!productGrid || !template) {
        return;
    }

    const cartContainers = [document.getElementById('cartItems'), document.getElementById('mobileCartItems')].filter(Boolean);
    const summaryElements = {
        items: [document.getElementById('summaryItems'), document.getElementById('mobileSummaryItems')],
        subtotal: document.getElementById('summarySubtotal'),
        surcharge: document.getElementById('surchargeAmount'),
        total: [document.getElementById('summaryTotal'), document.getElementById('mobileSummaryTotal'), document.getElementById('floatingTotal')]
    };
    const floatingBar = document.getElementById('floatingBar');
    const floatingItems = document.getElementById('floatingItems');
    const checkoutButtons = [document.getElementById('checkoutBtn'), document.getElementById('mobileCheckoutBtn')];
    const clearCartBtn = document.getElementById('clearCartBtn');
    const printBtn = document.getElementById('printBtn');
    const receiptEl = document.getElementById('receipt');
    const paymentPanel = document.getElementById('paymentPanel');
    const surchargePercentInput = document.getElementById('surchargePercentInput');
    const cashInput = document.getElementById('cashInput');
    const changeDisplay = document.getElementById('changeDisplay');
    const confirmPaymentBtn = document.getElementById('confirmPaymentBtn');
    const surchargeLabel = document.getElementById('surchargeLabel');
    if (surchargeLabel) surchargeLabel.textContent = state.surchargeName;
    if (surchargePercentInput) surchargePercentInput.value = state.surchargePercent;

    const shopInfo = {
        name: document.body.dataset.shopName || 'Cà Phê Mộc Viên',
        phone: document.body.dataset.shopPhone || '',
        address: document.body.dataset.shopAddress || '',
        qr: document.body.dataset.shopQr || ''
    };

    const tableLabel = document.getElementById('tableLabel');
    const tableLabelMobile = document.getElementById('tableLabelMobile');
    const tableSelectBtn = document.getElementById('tableSelectBtn');
    const tableSelectBtnMobile = document.getElementById('tableSelectBtnMobile');
    const tableGrid = document.getElementById('tableGrid');
    const takeAwayBtn = document.getElementById('takeAwayBtn');
    const tableModalElement = document.getElementById('tableModal');
    const modalElement = document.getElementById('itemEditModal');
    const tableModal = tableModalElement ? new bootstrap.Modal(tableModalElement) : null;
    const modal = new bootstrap.Modal(modalElement);
    const offcanvasElement = document.getElementById('cartOffcanvas');
    const offcanvas = offcanvasElement ? new bootstrap.Offcanvas(offcanvasElement) : null;

    const modalRefs = {
        name: document.getElementById('modalProductName'),
        qty: document.getElementById('modalQty'),
        note: document.getElementById('modalNote'),
        price: document.getElementById('modalPrice'),
        increase: document.getElementById('increaseQty'),
        decrease: document.getElementById('decreaseQty'),
        remove: document.getElementById('removeItemBtn'),
        save: document.getElementById('saveItemBtn'),
        noteTags: document.getElementById('noteTags')
    };

    let searchTimeout;

    function debounceSearch() {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(loadProducts, 250);
    }

    async function loadProducts() {
        const activeCategory = document.querySelector('.category-btn.active')?.dataset.category ?? 'all';
        const params = new URLSearchParams({ category: activeCategory });
        const searchTerm = productSearch?.value ?? '';
        if (searchTerm) params.append('search', searchTerm);
        const response = await fetch(`/api/pos/products?${params.toString()}`);
        renderProducts(await response.json());
    }

    function renderProducts(products) {
        productGrid.innerHTML = '';
        products.forEach(product => {
            const card = document.createElement('div');
            card.className = 'menu-card p-3';
            card.dataset.id = product.id;
            card.dataset.name = product.name;
            card.dataset.price = product.price;
            card.dataset.category = product.category;
            card.innerHTML = `
                ${product.imageUrl ? `<img src="${product.imageUrl}" alt="${product.name}">` : ''}
                <div>
                    <h6 class="fw-bold mb-1">${product.name}</h6>
                    <p class="text-muted small mb-2">${product.category}</p>
                </div>
                <div class="d-flex justify-content-between align-items-center">
                    <span class="fw-semibold">$${Number(product.price).toFixed(2)}</span>
                    <i class="fa fa-circle-plus text-primary"></i>
                </div>
            `;
            productGrid.appendChild(card);
        });
    }

    function addToCart(product) {
        if (state.selectedTable) ensureTableOccupied();
        // Chỉ merge với item cùng ID, không có note, và chưa được báo chế biến
        const existingIndex = state.cart.findIndex(item => 
            item.id === product.id && 
            !item.note && 
            !item.notified
        );
        if (existingIndex >= 0) {
            state.cart[existingIndex].quantity += 1;
        } else {
            state.cart.push({
                id: product.id,
                name: product.name,
                category: product.category,
                unitPrice: Number(product.price),
                quantity: 1,
                note: '',
                notified: false, // Mặc định là chưa báo chế biến
                priceOverride: null
            });
        }
        updateCartUI();
    }

    function createCartRow(item, index) {
        const fragment = template.content.cloneNode(true);
        const row = fragment.querySelector('.cart-item-row');
        row.dataset.index = index;

        const nameElement = row.querySelector('.item-name');
        nameElement.textContent = item.name;

        // Xóa các class cũ trước khi thêm class mới
        nameElement.classList.remove('item-notified', 'item-not-notified');
        
        // Áp dụng màu sắc dựa trên trạng thái notified
        // Màu đỏ cho món chưa báo chế biến, màu đen cho món đã báo chế biến
        if (item.notified) {
            nameElement.classList.add('item-notified');
        } else {
            nameElement.classList.add('item-not-notified');
        }

        row.querySelector('.item-note').textContent = item.note || '';
        row.querySelector('.item-price').textContent = effectivePrice(item).toFixed(2);
        row.querySelector('.item-qty').textContent = item.quantity;
        return fragment;
    }

    function updateCartUI() {
        cartContainers.forEach(container => container.innerHTML = '');
        state.cart.forEach((item, index) => {
            cartContainers.forEach(container => container.appendChild(createCartRow(item, index)));
        });
        const hasItems = state.cart.length > 0;
        const hasUnnotifiedItems = state.cart.some(item => !item.notified);
        // Cập nhật trạng thái cho cả nút desktop và mobile
        notifyKitchenButtons.forEach(btn => {
            if (hasItems && hasUnnotifiedItems) {
                btn.classList.remove('d-none');
                btn.disabled = false;
            } else {
                btn.classList.add('d-none');
                btn.disabled = true;
            }
        });
        const totalItems = state.cart.reduce((sum, item) => sum + item.quantity, 0);
        summaryElements.items.forEach(el => el && (el.textContent = totalItems));
        if (floatingItems) floatingItems.textContent = totalItems;
        if (floatingBar) floatingBar.classList.toggle('d-none', state.cart.length === 0);
        updateTotals();
        saveCartToStorage();
    }
    // Hàm xử lý báo chế biến (dùng chung cho cả desktop và mobile)
    async function handleNotifyKitchen() {
        const unnotifiedItems = state.cart.filter(item => !item.notified);

        if (unnotifiedItems.length === 0) {
            alert('Không có món mới để báo chế biến!');
            return;
        }

        // 1. Tạo mẫu in rút gọn cho phòng chế biến
        buildKitchenReceipt(unnotifiedItems);

        // 2. Lệnh in
        window.print();

        // 3. Lưu pending order vào database (lưu TẤT CẢ cart để đồng bộ)
        try {
            const totals = calculateTotals();
            const payload = {
                tableId: state.selectedTable?.id ?? null,
                tableNumber: tableLabel?.textContent || (state.selectedTable?.name ?? 'Mang về'),
                totalAmount: Number(totals.total.toFixed(2)),
                surchargePercent: totals.percent,
                surchargeAmount: Number(totals.surcharge.toFixed(2)),
                surchargeName: state.surchargeName,
                customerCash: null,
                changeAmount: null,
                items: state.cart.map(item => ({
                    productId: item.id,
                    quantity: item.quantity,
                    price: effectivePrice(item),
                    note: item.note
                }))
            };

            const response = await fetch('/api/pos/orders/pending', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                throw new Error('Failed to save pending order');
            }

            // 4. Cập nhật trạng thái các món chưa báo chế biến thành đã báo (màu đen)
            state.cart.forEach(item => {
                if (!item.notified) {
                    item.notified = true;
                }
            });

            updateCartUI();
        } catch (e) {
            console.error('Error saving pending order:', e);
            alert('Lỗi khi lưu đơn hàng: ' + e.message);
        }
    }

    // Gắn event listener cho cả nút desktop và mobile
    notifyKitchenButtons.forEach(btn => {
        btn?.addEventListener('click', handleNotifyKitchen);
    });

    function buildKitchenReceipt(items) {
        if (!receiptEl) return;

        const itemsHtml = items.map(item => `
        <tr>
            <td style="font-size: 18px; padding: 5px 0;">
                <strong>${item.name} x${item.quantity}</strong>
                ${item.note ? `<div style="font-size:14px;">📝 Ghi chú: ${item.note}</div>` : ''}
            </td>
        </tr>
    `).join('');

        receiptEl.innerHTML = `
        <div class="receipt kitchen-receipt" style="text-align: center; font-family: monospace;">
            <h2 style="margin-bottom: 5px;"> PHIẾU CHẾ BIẾN </h2>
            <div style="border-bottom: 1px dashed #000; margin-bottom: 10px;">
                Bàn: ${state.selectedTable?.name ?? 'Mang về'} | ${new Date().toLocaleTimeString('vi-VN')}
            </div>
            <table style="width: 100%; text-align: left;">
                <tbody>${itemsHtml}</tbody>
            </table>
            <div style="border-top: 1px dashed #000; margin-top: 10px; padding-top: 5px;">
                --- Chúc pha chế ngon miệng ---
            </div>
        </div>
    `;
    }

    function effectivePrice(item) {
        return item.priceOverride !== null ? item.priceOverride : item.unitPrice;
    }

    function calculateTotals() {
        const subtotal = state.cart.reduce((sum, item) => sum + effectivePrice(item) * item.quantity, 0);
        const percent = Number(surchargePercentInput?.value ?? state.surchargePercent) || 0;
        const surcharge = subtotal * percent / 100;
        const total = subtotal + surcharge;
        const change = Math.max(0, (state.cashGiven || 0) - total);
        return { subtotal, surcharge, total, change, percent };
    }

    function updateTotals() {
        const { subtotal, surcharge, total, change } = calculateTotals();
        if (summaryElements.subtotal) summaryElements.subtotal.textContent = formatCurrency(subtotal);
        if (summaryElements.surcharge) summaryElements.surcharge.textContent = formatCurrency(surcharge);
        summaryElements.total.forEach(el => el && (el.textContent = formatCurrency(total)));
        if (changeDisplay) changeDisplay.textContent = formatCurrency(change);
    }

    function formatCurrency(value) {
        return `$${(value || 0).toFixed(2)}`;
    }

    function adjustQuantity(index, delta) {
        const item = state.cart[index];
        if (!item) return;
        item.quantity += delta;
        if (item.quantity <= 0) {
            state.cart.splice(index, 1);
        }
        updateCartUI();
    }

    function openEditModal(index) {
        state.editingIndex = index;
        const item = state.cart[index];
        if (!item) return;
        modalRefs.name.textContent = item.name;
        modalRefs.qty.textContent = item.quantity;
        modalRefs.note.value = item.note;
        modalRefs.price.value = effectivePrice(item);
        modalRefs.price.disabled = !state.isAdmin;
        modal.show();
    }

    modalRefs.increase.addEventListener('click', () => {
        if (state.editingIndex === null) return;
        state.cart[state.editingIndex].quantity += 1;
        modalRefs.qty.textContent = state.cart[state.editingIndex].quantity;
    });

    modalRefs.decrease.addEventListener('click', () => {
        if (state.editingIndex === null) return;
        const item = state.cart[state.editingIndex];
        if (item.quantity > 1) {
            item.quantity -= 1;
            modalRefs.qty.textContent = item.quantity;
        }
    });

    modalRefs.noteTags.querySelectorAll('.note-tag').forEach(btn => {
        btn.addEventListener('click', () => {
            modalRefs.note.value = btn.dataset.value;
        });
    });

    modalRefs.remove.addEventListener('click', () => {
        if (state.editingIndex === null) return;
        state.cart.splice(state.editingIndex, 1);
        modal.hide();
        updateCartUI();
    });

    modalRefs.save.addEventListener('click', () => {
        if (state.editingIndex === null) return;
        const item = state.cart[state.editingIndex];
        item.note = modalRefs.note.value;
        if (state.isAdmin) {
            const override = parseFloat(modalRefs.price.value);
            if (!isNaN(override) && override >= 0) {
                item.priceOverride = override;
            }
        }
        modal.hide();
        updateCartUI();
    });

    function enableCheckoutMode() {
        state.checkoutMode = true;
        paymentPanel?.classList.remove('d-none');
        confirmPaymentBtn?.classList.remove('d-none');
        checkoutButtons.forEach(btn => btn && (btn.textContent = 'Hủy'));
    }

    function disableCheckoutMode(resetCash = true) {
        state.checkoutMode = false;
        paymentPanel?.classList.add('d-none');
        confirmPaymentBtn?.classList.add('d-none');
        checkoutButtons.forEach(btn => btn && (btn.textContent = 'Checkout'));
        if (resetCash) {
            state.cashGiven = 0;
            if (cashInput) cashInput.value = '';
            updateTotals();
        }
    }

    checkoutButtons.forEach(btn => btn?.addEventListener('click', () => {
        if (state.checkoutMode) {
            disableCheckoutMode();
        } else {
            enableCheckoutMode();
        }
    }));

    confirmPaymentBtn?.addEventListener('click', async () => {
        const prepared = prepareCheckout();
        if (!prepared) return;
        buildReceipt(prepared.totals);
        window.print();
        await finalizeCheckout(prepared.payload);
    });

    function prepareCheckout() {
        if (state.cart.length === 0) {
            alert('Cart is empty');
            return null;
        }
        const totals = calculateTotals();
        if (state.cashGiven < totals.total) {
            alert('Số tiền khách đưa chưa đủ');
            return null;
        }
        const payload = {
            tableId: state.selectedTable?.id ?? null,
            tableNumber: tableLabel.textContent,
            totalAmount: Number(totals.total.toFixed(2)),
            surchargePercent: totals.percent,
            surchargeAmount: Number(totals.surcharge.toFixed(2)),
            surchargeName: state.surchargeName,
            customerCash: Number((state.cashGiven || 0).toFixed(2)),
            changeAmount: Number(totals.change.toFixed(2)),
            items: state.cart.map(item => ({
                productId: item.id,
                quantity: item.quantity,
                price: effectivePrice(item),
                note: item.note
            }))
        };
        return { totals, payload };
    }

    async function finalizeCheckout(preparedPayload) {
        const payload = preparedPayload ?? prepareCheckout()?.payload;
        if (!payload) return;
        try {
            const response = await fetch('/api/pos/orders', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!response.ok) throw new Error('Failed to submit order');
            clearCartStorage(currentCartKey());
            await releaseCurrentTable();
            state.cart = [];
            state.cashGiven = 0;
            if (cashInput) cashInput.value = '';
            disableCheckoutMode(false);
            updateCartUI();
            alert('Order submitted!');
            offcanvas?.hide();
        } catch (e) {
            alert(e.message);
        }
    }

    clearCartBtn?.addEventListener('click', () => {
        if (state.cart.length === 0) return;
        if (confirm('Clear current order?')) {
            state.cart = [];
            updateCartUI();
            disableCheckoutMode();
        }
    });

    printBtn?.addEventListener('click', () => {
        if (state.cart.length === 0) {
            alert('Chưa có sản phẩm để in');
            return;
        }
        buildReceipt();
        setTimeout(() => window.print(), 200);
    });

    function buildReceipt(precomputedTotals) {
        if (!receiptEl) return;
        const totals = precomputedTotals || calculateTotals();
        const itemsHtml = state.cart.map(item => `
            <tr>
                <td>
                    <div>${item.name} x${item.quantity}</div>
                    ${item.note ? `<div style="font-size:11px;font-style:italic;">${item.note}</div>` : ''}
                </td>
                <td class="text-right">$${(effectivePrice(item) * item.quantity).toFixed(2)}</td>
            </tr>
        `).join('');

        receiptEl.innerHTML = `
            <div class="receipt">
                <div class="receipt-header">
                    <h3>${shopInfo.name}</h3>
                    <div>${shopInfo.address || ''}</div>
                    <div>${new Date().toLocaleString('vi-VN')}</div>
                    <div>Bàn: ${state.selectedTable?.name ?? 'Mang về'}</div>
                </div>
                <table>
                    <tbody>${itemsHtml}</tbody>
                </table>
                <div class="total-section">
                    <div class="d-flex">
                        <span>Subtotal</span>
                        <span>${formatCurrency(totals.subtotal)}</span>
                    </div>
                    <div class="d-flex">
                        <span>${state.surchargeName} (${totals.percent}%)</span>
                        <span>${formatCurrency(totals.surcharge)}</span>
                    </div>
                    <div class="d-flex fw-bold">
                        <span>Tổng</span>
                        <span>${formatCurrency(totals.total)}</span>
                    </div>
                    <div class="d-flex">
                        <span>Khách đưa</span>
                        <span>${formatCurrency(state.cashGiven)}</span>
                    </div>
                    <div class="d-flex">
                        <span>Tiền thừa</span>
                        <span>${formatCurrency(totals.change)}</span>
                    </div>
                </div>
                <div class="receipt-footer">
                    <div>Cảm ơn quý khách!</div>
                    <div>${shopInfo.phone || ''}</div>
                    ${shopInfo.qr ? `<img src="${shopInfo.qr}" alt="QR">` : ''}
                </div>
            </div>
        `;
    }

    productGrid.addEventListener('click', event => {
        const card = event.target.closest('.menu-card');
        if (!card) return;
        addToCart({
            id: Number(card.dataset.id),
            name: card.dataset.name,
            price: Number(card.dataset.price),
            category: card.dataset.category
        });
    });

    cartContainers.forEach(container => container.addEventListener('click', event => {
        if (event.target.closest('.btn-increase')) {
            adjustQuantity(Number(event.target.closest('.cart-item-row').dataset.index), 1);
        } else if (event.target.closest('.btn-decrease')) {
            adjustQuantity(Number(event.target.closest('.cart-item-row').dataset.index), -1);
        } else if (event.target.closest('.cart-item-row') && !event.target.closest('button')) {
            openEditModal(Number(event.target.closest('.cart-item-row').dataset.index));
        }
    }));

    categoryButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            categoryButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            loadProducts();
        });
    });

    productSearch?.addEventListener('input', debounceSearch);

    document.addEventListener('keydown', event => {
        if (event.key === 'F1') {
            event.preventDefault();
            productSearch?.focus();
        }
    });

    surchargePercentInput?.addEventListener('input', () => {
        state.surchargePercent = Number(surchargePercentInput.value) || 0;
        updateTotals();
        saveCartToStorage();
    });

    cashInput?.addEventListener('input', () => {
        state.cashGiven = Number(cashInput.value) || 0;
        updateTotals();
        saveCartToStorage();
    });

    tableSelectBtn?.addEventListener('click', async () => {
        await loadTables();
        tableModal?.show();
    });

    tableSelectBtnMobile?.addEventListener('click', async () => {
        await loadTables();
        tableModal?.show();
    });

    takeAwayBtn?.addEventListener('click', async () => {
        await setTakeAway();
        tableModal?.hide();
    });

    async function loadTables() {
        const response = await fetch('/api/pos/tables');
        state.tables = await response.json();
        renderTableGrid();
    }

    function renderTableGrid() {
        if (!tableGrid) return;
        tableGrid.innerHTML = '';
        state.tables.forEach(table => {
            const col = document.createElement('div');
            col.className = 'col-4 col-md-3';
            col.innerHTML = `
                <div class="table-card ${table.status.toLowerCase()}">
                    <div class="d-flex justify-content-between align-items-center">
                        <strong>${table.name}</strong>
                        <span class="badge ${table.status === 'OCCUPIED' ? 'bg-danger' : (table.status === 'DISABLED' ? 'bg-secondary' : 'bg-success')}">
                            ${table.status === 'OCCUPIED' ? 'Đang dùng' : (table.status === 'DISABLED' ? 'Ngưng' : 'Trống')}
                        </span>
                    </div>
                    <small class="text-muted">${table.status === 'DISABLED'
                    ? 'Ẩn khỏi POS'
                    : (table.occupiedAt ? formatDuration(table.occupiedAt) : 'Chưa có khách')
                }</small>
                </div>
            `;
            const card = col.querySelector('.table-card');
            if (table.status !== 'DISABLED') {
                card.addEventListener('click', () => selectTable(table));
            }
            tableGrid.appendChild(col);
        });
    }

    function formatDuration(isoString) {
        if (!isoString) return '';
        const start = new Date(isoString);
        const diffMinutes = Math.max(1, Math.floor((Date.now() - start.getTime()) / 60000));
        const hours = Math.floor(diffMinutes / 60);
        const minutes = diffMinutes % 60;
        if (hours > 0) return `${hours}h ${minutes}p`;
        return `${minutes} phút`;
    }

    async function selectTable(table) {
        disableCheckoutMode();
        // Lưu cart hiện tại của bàn cũ trước khi chuyển
        persistCurrentCart();
        
        state.selectedTable = { ...table };
        updateTableLabel(table.name);
        
        // Load pending orders từ database trước
        await loadPendingOrdersFromServer();
        
        // Nếu không có pending orders từ server, load từ localStorage
        if (state.cart.length === 0) {
            loadCartFromStorage();
        }
        
        tableModal?.hide();
    }

    async function releaseCurrentTable() {
        disableCheckoutMode();
        if (state.selectedTable) {
            await fetch(`/api/pos/tables/${state.selectedTable.id}/release`, { method: 'POST' });
        }
        state.selectedTable = null;
        updateTableLabel('Mang về');
        loadCartFromStorage();
    }

    async function setTakeAway() {
        disableCheckoutMode();
        // Lưu cart hiện tại của bàn cũ trước khi chuyển
        persistCurrentCart();
        
        state.selectedTable = null;
        updateTableLabel('Mang về');
        
        // Load pending orders từ database cho takeaway
        await loadPendingOrdersFromServer();
        
        // Nếu không có pending orders từ server, load từ localStorage
        if (state.cart.length === 0) {
            loadCartFromStorage();
        }
    }

    function updateTableLabel(value) {
        if (tableLabel) tableLabel.textContent = value;
        if (tableLabelMobile) tableLabelMobile.textContent = value;
    }

    function currentCartKey() {
        const id = state.selectedTable?.id ? `TABLE_${state.selectedTable.id}` : 'TAKEAWAY';
        return `${CART_STORAGE_PREFIX}${id}`;
    }

    function saveCartToStorage() {
        const payload = {
            items: state.cart,
            surchargePercent: Number(surchargePercentInput?.value ?? state.surchargePercent) || 0,
            cashGiven: state.cashGiven
        };
        localStorage.setItem(currentCartKey(), JSON.stringify(payload));
    }

    function clearCartStorage(key) {
        localStorage.removeItem(key);
    }

    function persistCurrentCart() {
        saveCartToStorage();
    }

    function loadCartFromStorage() {
        const raw = localStorage.getItem(currentCartKey());
        if (raw) {
            try {
                const parsed = JSON.parse(raw);
                state.cart = parsed.items || [];
                state.cashGiven = parsed.cashGiven || 0;
                const percent = parsed.surchargePercent ?? state.surchargePercent;
                state.surchargePercent = percent;
                if (surchargePercentInput) surchargePercentInput.value = percent;
                if (cashInput) cashInput.value = state.cashGiven || '';
            } catch {
                state.cart = [];
            }
        } else {
            state.cart = [];
            state.cashGiven = 0;
            if (cashInput) cashInput.value = '';
            if (surchargePercentInput) surchargePercentInput.value = state.surchargePercent;
        }
        updateCartUI();
    }

    async function ensureTableOccupied() {
        if (!state.selectedTable || state.selectedTable.status === 'OCCUPIED') return;
        try {
            const res = await fetch(`/api/pos/tables/${state.selectedTable.id}/occupy`, { method: 'POST' });
            if (res.ok) {
                state.selectedTable = await res.json();
            }
        } catch (err) {
            console.error(err);
        }
    }

    async function loadPendingOrdersFromServer() {
        try {
            const tableId = state.selectedTable?.id ?? null;
            const url = tableId 
                ? `/api/pos/orders/pending?tableId=${tableId}`
                : '/api/pos/orders/pending';
            
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error('Failed to load pending orders');
            }
            
            const data = await response.json();
            if (data.items && data.items.length > 0) {
                // Convert server items to cart format và merge với cart hiện tại
                const serverItems = data.items.map(item => ({
                    id: item.id,
                    name: item.name,
                    category: item.category,
                    unitPrice: item.unitPrice,
                    quantity: item.quantity,
                    note: item.note || '',
                    notified: true, // Tất cả items từ server đều đã được báo chế biến
                    priceOverride: item.priceOverride
                }));

                // Merge: Nếu có item cùng ID và note trong cart hiện tại, giữ lại cart hiện tại
                // Nếu không có pending orders, dùng cart từ server
                // Nếu có cả hai, ưu tiên server items (vì đã được sync)
                state.cart = serverItems;
                
                // Cập nhật surcharge từ server
                if (data.surchargePercent !== undefined) {
                    state.surchargePercent = data.surchargePercent;
                    if (surchargePercentInput) surchargePercentInput.value = data.surchargePercent;
                }
                
                if (data.surchargeName) {
                    state.surchargeName = data.surchargeName;
                }
                
                // Lưu vào localStorage để sync
                saveCartToStorage();
                updateCartUI();
            } else {
                // Không có pending orders từ server, giữ nguyên cart hiện tại (sẽ load từ localStorage sau)
            }
        } catch (err) {
            console.error('Error loading pending orders:', err);
            // Nếu lỗi, giữ nguyên cart hiện tại
        }
    }

    loadProducts();
    loadCartFromStorage();
})();

