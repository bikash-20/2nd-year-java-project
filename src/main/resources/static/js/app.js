// ── Mobile nav ───────────────────────────────────────────────────────────────
function toggleNav() {
    document.getElementById('navLinks').classList.toggle('open');
}

// ── Chatbot toggle and messaging ────────────────────────────────────────
function toggleChatbot() {
    const modal = document.getElementById('chatbot-modal');
    const btn = document.getElementById('chatbot-toggle-btn');
    if (modal.style.display === 'none' || !modal.style.display) {
        modal.style.display = 'flex';
        if (btn) btn.style.display = 'none';
    } else {
        modal.style.display = 'none';
        if (btn) btn.style.display = 'flex';
    }
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

    // Append user message to history
    chatHistory.push({ role: "user", content: msg });

    // Prepare payload for OpenRouter via backend
    const payload = {
        messages: chatHistory
    };

    fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => res.json().then(data => ({ status: res.status, ok: res.ok, data })))
    .then(({ status, ok, data }) => {
        if (!ok || data.error) {
            throw new Error(data.error || 'Server returned status ' + status);
        }
        const reply = data.choices?.[0]?.message?.content || 'No response.';
        
        // Append AI response to history
        chatHistory.push({ role: "assistant", content: reply });

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
// Global chat history to maintain context and inject AI personality
let chatHistory = [
    {
        role: "system",
        content: "You are the official AI assistant for Rentify, a car rental web application built by BIKASH TALUKDER during his 2nd year of CSE. Your primary purpose is to help customers and administrators with the app's features, including car prices, billing, available cars, and revenue tracking. \n\nIMPORTANT RULES:\n1. When the user sends a greeting (e.g., 'hi', 'hello', 'hlw'), you MUST greet them back, introduce yourself as the Rentify assistant built by Bikash, briefly list what you can help with, and ask them what they would like to know.\n2. LIVE DATA: The system will silently inject real-time database stats (revenue, available cars, active rentals) into the conversation before the user's message. Use this live data confidently to answer any specific questions about available cars or revenue!\n3. If they ask out-of-context questions, answer politely but remind them you are the Rentify assistant."
    }
];

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