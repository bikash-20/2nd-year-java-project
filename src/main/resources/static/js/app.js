// ── Mobile nav ───────────────────────────────────────────────────────────────
function toggleNav() {
    document.getElementById('navLinks').classList.toggle('open');
}

// ── Chatbot toggle and messaging ────────────────────────────────────────
function toggleChatbot() {
    const modal = document.getElementById('chatbot-modal');
    if (!modal) return;
    const isVisible = modal.style.display && modal.style.display !== 'none';
    modal.style.display = isVisible ? 'none' : 'block';
}

function sendMessage() {
    const input = document.getElementById('chatbot-input');
    const body = document.getElementById('chatbot-body');
    if (!input || !body) return;
    const msg = input.value.trim();
    if (!msg) return;

    // Append user message
    const userDiv = document.createElement('div');
    userDiv.style.marginBottom = '8px';
    userDiv.style.color = '#333';
    userDiv.textContent = 'You: ' + msg;
    body.appendChild(userDiv);
    input.value = '';
    body.scrollTop = body.scrollHeight;

    // Prepare payload for OpenRouter via backend
    const payload = {
        model: "openrouter/auto",
        messages: [{ role: "user", content: msg }]
    };

    fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => res.json())
    .then(data => {
        const reply = data.choices?.[0]?.message?.content || 'No response.';
        const aiDiv = document.createElement('div');
        aiDiv.style.marginBottom = '8px';
        aiDiv.style.color = '#333';
        aiDiv.textContent = 'AI: ' + reply;
        body.appendChild(aiDiv);
        body.scrollTop = body.scrollHeight;
    })
    .catch(err => {
        const errDiv = document.createElement('div');
        errDiv.style.marginBottom = '8px';
        errDiv.style.color = 'var(--red)';
        errDiv.textContent = 'AI: (error) ' + err.message;
        body.appendChild(errDiv);
        body.scrollTop = body.scrollHeight;
    });
}

// ── Date-based price preview ─────────────────────────────────────────────────
function updatePrice() {
    const select = document.getElementById('carId');
    const startInput = document.getElementById('startDate');
    const endInput = document.getElementById('endDate');
    const preview = document.getElementById('pricePreview');
    const amountEl = document.getElementById('previewAmount');
    const daysEl = document.getElementById('previewDays');

    if (!select || !startInput || !endInput || !preview) return;

    const selected = select.options[select.selectedIndex];
    const price = parseFloat(selected?.dataset?.price || 0);
    const start = startInput.value ? new Date(startInput.value) : null;
    const end = endInput.value ? new Date(endInput.value) : null;

    // Auto set endDate min to day after startDate
    if (startInput.value) {
        const minEnd = new Date(startInput.value);
        minEnd.setDate(minEnd.getDate() + 1);
        endInput.min = minEnd.toISOString().split('T')[0];
    }

    if (price > 0 && start && end && end > start) {
        const days = Math.round((end - start) / (1000 * 60 * 60 * 24));
        const total = price * days;
        daysEl.textContent = days + (days === 1 ? ' day' : ' days');
        amountEl.textContent = '$' + total.toLocaleString('en-US', { minimumFractionDigits: 2 });
        preview.style.display = 'flex';
    } else {
        preview.style.display = 'none';
    }
}

// ── Click car in sidebar → uses data attributes (Thymeleaf 3.1 safe) ─────────
function selectCarFromData(el) {
    const carId = el.dataset.carid;
    const price = el.dataset.price;
    const select = document.getElementById('carId');
    if (!select || !carId) return;
    select.value = carId;
    updatePrice();
    select.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

// ── Legacy support ────────────────────────────────────────────────────────────
function selectCar(carId, price) {
    const select = document.getElementById('carId');
    if (!select) return;
    select.value = carId;
    updatePrice();
}

// ── Set default startDate to today ────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    const startInput = document.getElementById('startDate');
    if (startInput && !startInput.value) {
        startInput.value = new Date().toISOString().split('T')[0];
    }
    const params = new URLSearchParams(window.location.search);
    const carId = params.get('carId');
    if (carId) {
        const select = document.getElementById('carId');
        if (select) { select.value = carId; updatePrice(); }
    }
});