const form = document.querySelector("#message-form");
const input = document.querySelector("#message-input");
const conversation = document.querySelector("#conversation");
const sendButton = document.querySelector("#send-button");
const status = document.querySelector("#status");
const reminderForm = document.querySelector("#reminder-form");
const reminderTime = document.querySelector("#reminder-time");
const reminderMessage = document.querySelector("#reminder-message");
const reminderList = document.querySelector("#reminder-list");
const reminderStatus = document.querySelector("#reminder-status");

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

function formatReminderTime(reminder) {
    const date = new Date(reminder.scheduledAt);
    try {
        return new Intl.DateTimeFormat(undefined, {
            dateStyle: "medium",
            timeStyle: "short",
            timeZone: reminder.timeZone
        }).format(date);
    } catch {
        return `${date.toLocaleString()} (${reminder.timeZone})`;
    }
}

async function loadReminders() {
    try {
        const response = await fetch("/api/reminders");
        if (!response.ok) {
            throw new Error(`Request failed (${response.status})`);
        }
        const reminders = await response.json();
        reminderList.replaceChildren();

        const dueReminders = reminders.filter((reminder) => reminder.status === "DUE");
        reminderStatus.textContent = dueReminders.length
            ? `Due now: ${dueReminders.map((reminder) => reminder.message).join("; ")}`
            : "";

        for (const reminder of reminders) {
            const item = document.createElement("li");
            item.className = `reminder-item ${reminder.status.toLowerCase()}`;

            const details = document.createElement("span");
            details.textContent = `${reminder.message} — ${formatReminderTime(reminder)}`;
            item.append(details);

            const action = document.createElement("button");
            action.type = "button";
            action.textContent = reminder.status === "DUE" ? "Acknowledge" : "Cancel";
            action.addEventListener("click", async () => {
                const endpoint = reminder.status === "DUE"
                    ? `/api/reminders/${reminder.id}/acknowledge`
                    : `/api/reminders/${reminder.id}`;
                const method = reminder.status === "DUE" ? "POST" : "DELETE";
                try {
                    const actionResponse = await fetch(endpoint, { method });
                    if (!actionResponse.ok) {
                        throw new Error(`Request failed (${actionResponse.status})`);
                    }
                    await loadReminders();
                } catch (error) {
                    reminderStatus.textContent = "Could not update this reminder.";
                    console.error("Reminder update failed", error);
                }
            });
            item.append(action);
            reminderList.append(item);
        }
    } catch (error) {
        reminderStatus.textContent = "Could not load reminders.";
        console.error("Reminder list request failed", error);
    }
}

reminderForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!reminderForm.reportValidity()) {
        return;
    }
    try {
        const response = await fetch("/api/reminders", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                time: reminderTime.value,
                message: reminderMessage.value,
                timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
            })
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || `Request failed (${response.status})`);
        }
        reminderForm.reset();
        reminderStatus.textContent = "Reminder added.";
        await loadReminders();
    } catch (error) {
        reminderStatus.textContent = error.message || "Could not add reminder.";
        console.error("Reminder creation failed", error);
    }
});

document.querySelector("#refresh-reminders").addEventListener("click", loadReminders);
loadReminders();
window.setInterval(loadReminders, 15000);
