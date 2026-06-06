// ── Mobile nav ──────────────────────────────────────────────────────────────
function toggleNav() {
    document.getElementById('navLinks').classList.toggle('open');
}

// ── Date-based price preview ─────────────────────────────────────────────────
function updatePrice() {
    const select    = document.getElementById('carId');
    const startInput = document.getElementById('startDate');
    const endInput   = document.getElementById('endDate');
    const preview    = document.getElementById('pricePreview');
    const amountEl   = document.getElementById('previewAmount');
    const daysEl     = document.getElementById('previewDays');

    if (!select || !startInput || !endInput || !preview) return;

    const selected = select.options[select.selectedIndex];
    const price    = parseFloat(selected?.dataset?.price || 0);
    const start    = startInput.value ? new Date(startInput.value) : null;
    const end      = endInput.value   ? new Date(endInput.value)   : null;

    // Auto set endDate min to day after startDate
    if (startInput.value) {
        const minEnd = new Date(startInput.value);
        minEnd.setDate(minEnd.getDate() + 1);
        endInput.min = minEnd.toISOString().split('T')[0];
    }

    if (price > 0 && start && end && end > start) {
        const diffMs   = end - start;
        const days     = Math.round(diffMs / (1000 * 60 * 60 * 24));
        const total    = price * days;
        daysEl.textContent  = days + (days === 1 ? ' day' : ' days');
        amountEl.textContent = '$' + total.toLocaleString('en-US', { minimumFractionDigits: 2 });
        preview.style.display = 'flex';
    } else {
        preview.style.display = 'none';
    }
}

// ── Click car in sidebar → select in dropdown ────────────────────────────────
function selectCar(carId, price) {
    const select = document.getElementById('carId');
    if (!select) return;
    select.value = carId;
    updatePrice();
    select.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

// ── Pre-select carId from URL param ─────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(window.location.search);
    const carId  = params.get('carId');
    if (carId) {
        const select = document.getElementById('carId');
        if (select) { select.value = carId; updatePrice(); }
    }

    // Set default startDate to today
    const startInput = document.getElementById('startDate');
    if (startInput && !startInput.value) {
        const today = new Date().toISOString().split('T')[0];
        startInput.value = today;
    }
});
