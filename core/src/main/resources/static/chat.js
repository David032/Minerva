const form = document.querySelector("#message-form");
const input = document.querySelector("#message-input");
const conversation = document.querySelector("#conversation");
const sendButton = document.querySelector("#send-button");
const status = document.querySelector("#status");

function addMessage(text, role) {
    const welcome = conversation.querySelector(".welcome");
    if (welcome) {
        welcome.remove();
    }

    const message = document.createElement("div");
    message.className = `message ${role}`;
    message.textContent = text;
    conversation.append(message);
    conversation.scrollTop = conversation.scrollHeight;
}

function resizeInput() {
    input.style.height = "auto";
    input.style.height = `${input.scrollHeight}px`;
}

input.addEventListener("input", resizeInput);
input.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        form.requestSubmit();
    }
});

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const message = input.value.trim();
    if (!message || sendButton.disabled) {
        return;
    }

    addMessage(message, "user");
    input.value = "";
    resizeInput();
    sendButton.disabled = true;
    status.classList.remove("error");
    status.textContent = "Minerva is thinking...";

    try {
        const response = await fetch("/message", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: new URLSearchParams({ message })
        });
        if (!response.ok) {
            throw new Error(`Request failed (${response.status})`);
        }

        addMessage(await response.text(), "assistant");
        status.textContent = "";
    } catch (error) {
        status.classList.add("error");
        status.textContent = "Could not reach Minerva. Please try again.";
        console.error("Chat request failed", error);
    } finally {
        sendButton.disabled = false;
        input.focus();
    }
});
