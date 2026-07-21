// ── Mobile nav ───────────────────────────────────────────────────────────────
function toggleNav() {
    document.getElementById('navLinks').classList.toggle('open');
}

// ── Markdown + LaTeX renderer ────────────────────────────────────────────────
function renderRichText(text) {
    if (!text) return '';
    // Configure marked
    if (typeof marked !== 'undefined') {
        marked.setOptions({
            breaks: true,
            gfm: true
        });
        // Convert markdown to HTML (synchronous)
        let html = marked.parse(text);
        // Sanitize
        if (typeof DOMPurify !== 'undefined') {
            html = DOMPurify.sanitize(html);
        }
        // Render LaTeX: inline $...$ and display $$...$$
        if (typeof katex !== 'undefined') {
            // Display math $$...$$
            html = html.replace(/\$\$([\s\S]*?)\$\$/g, (_, eq) => {
                try {
                    return katex.renderToString(eq.trim(), { displayMode: true, throwOnError: false });
                } catch (e) {
                    return '<span class="katex-error">' + eq + '</span>';
                }
            });
            // Inline math $...$
            html = html.replace(/\$([^\$]*?)\$/g, (_, eq) => {
                try {
                    return katex.renderToString(eq.trim(), { displayMode: false, throwOnError: false });
                } catch (e) {
                    return '<span class="katex-error">$' + eq + '$</span>';
                }
            });
        }
        return html;
    }
    // Fallback: escape HTML
    return text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

// ── Quick ask from hint chips ────────────────────────────────────────────────
function quickAsk(question) {
    const input = document.getElementById('chatbot-input');
    if (input) {
        input.value = question;
        sendMessage();
    }
}

// ── Add a message bubble to the chat ─────────────────────────────────────────
function addMessage(text, role) {
    const body = document.getElementById('chatbot-body');
    if (!body) return;

    // Remove welcome message on first interaction
    const welcome = body.querySelector('.chatbot-welcome');
    if (welcome) welcome.remove();

    const bubble = document.createElement('div');
    bubble.className = 'chatbot-msg ' + (role === 'user' ? 'msg-user' : 'msg-ai');

    if (role === 'user') {
        // User message: plain text in bubble
        bubble.textContent = text;
    } else {
        // AI message: rich rendered content
        const inner = document.createElement('div');
        inner.className = 'msg-content';
        inner.innerHTML = renderRichText(text);
        bubble.appendChild(inner);
    }

    body.appendChild(bubble);
    body.scrollTop = body.scrollHeight;
}

// ── Show typing indicator ────────────────────────────────────────────────────
function showTyping() {
    const body = document.getElementById('chatbot-body');
    if (!body) return;
    const existing = document.getElementById('typing-indicator');
    if (existing) existing.remove();

    const div = document.createElement('div');
    div.id = 'typing-indicator';
    div.className = 'chatbot-msg msg-ai typing';
    div.innerHTML = '<div class="typing-dots"><span></span><span></span><span></span></div>';
    body.appendChild(div);
    body.scrollTop = body.scrollHeight;
}

function hideTyping() {
    const el = document.getElementById('typing-indicator');
    if (el) el.remove();
}

// ── Chatbot toggle ───────────────────────────────────────────────────────────
function toggleChatbot() {
    const modal = document.getElementById('chatbot-modal');
    const btn = document.getElementById('chatbot-toggle-btn');
    if (!modal) return;
    if (modal.classList.contains('open')) {
        modal.classList.remove('open');
        if (btn) btn.classList.remove('hidden');
    } else {
        modal.classList.add('open');
        if (btn) btn.classList.add('hidden');
    }
}

// ── Send message ─────────────────────────────────────────────────────────────
function sendMessage() {
    const input = document.getElementById('chatbot-input');
    const body = document.getElementById('chatbot-body');
    if (!input || !body) return;
    const msg = input.value.trim();
    if (!msg) return;

    // Add user message
    addMessage(msg, 'user');
    input.value = '';

    // Add to history
    chatHistory.push({ role: "user", content: msg });

    // Show typing
    showTyping();

    // Fetch AI response
    fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ messages: chatHistory })
    })
    .then(res => res.json().then(data => ({ status: res.status, ok: res.ok, data })))
    .then(({ status, ok, data }) => {
        hideTyping();
        if (!ok || data.error) {
            throw new Error(data.error || 'Status ' + status);
        }
        const reply = data.choices?.[0]?.message?.content || 'No response.';
        chatHistory.push({ role: "assistant", content: reply });
        addMessage(reply, 'ai');
    })
    .catch(err => {
        hideTyping();
        const errMsg = '😅 Sorry, I ran into an issue: ' + err.message;
        addMessage(errMsg, 'ai');
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

// ── Click car in sidebar ─────────────────────────────────────────────────────
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

// ── Image fallback ───────────────────────────────────────────────────────────
function imgFallback(el) {
    if (el && el.parentElement) {
        el.parentElement.innerHTML = '<div class="about-initials">BT</div>';
    }
}

// ── Theme Toggle ─────────────────────────────────────────────────────────────
function toggleTheme() {
    const html = document.documentElement;
    const btn = document.getElementById('theme-toggle');
    if (html.getAttribute('data-theme') === 'light') {
        html.removeAttribute('data-theme');
        if (btn) btn.innerHTML = '&#9681;';
        localStorage.setItem('rentify-theme', 'dark');
    } else {
        html.setAttribute('data-theme', 'light');
        if (btn) btn.innerHTML = '&#9790;';
        localStorage.setItem('rentify-theme', 'light');
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const saved = localStorage.getItem('rentify-theme');
    const btn = document.getElementById('theme-toggle');
    if (saved === 'light') {
        document.documentElement.setAttribute('data-theme', 'light');
        if (btn) btn.innerHTML = '&#9790;';
    }
});

// ── Chat history ─────────────────────────────────────────────────────────────
let chatHistory = [
    {
        role: "system",
        content: "You are the official AI assistant for Rentify, a car rental web application built by BIKASH TALUKDER during his 2nd year of CSE. Your primary purpose is to help customers and administrators with the app's features, including car prices, billing, available cars, and revenue tracking. \n\nIMPORTANT RULES:\n1. When the user sends a greeting (e.g., 'hi', 'hello', 'hlw'), you MUST greet them back, introduce yourself as the Rentify assistant built by Bikash, briefly list what you can help with, and ask them what they would like to know.\n2. LIVE DATA: The system will silently inject real-time database stats (revenue, available cars, active rentals) into the conversation before the user's message. Use this live data confidently to answer any specific questions about available cars or revenue!\n3. FORMATTING: You should use Markdown formatting for your responses - use **bold** for emphasis, `code` for technical terms, bullet lists for multiple items, and $LaTeX$ math notation when showing calculations like $total = rate \\times days$. This makes your answers beautiful and professional!\n4. If they ask out-of-context questions, answer politely but remind them you are the Rentify assistant."
    }
];

// ── DOMContentLoaded ─────────────────────────────────────────────────────────
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