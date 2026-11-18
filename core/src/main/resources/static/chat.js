const form = document.querySelector("#message-form");
const input = document.querySelector("#message-input");
const conversation = document.querySelector("#conversation");
const sendButton = document.querySelector("#send-button");
const status = document.querySelector("#status");
const imageInput = document.querySelector("#image-input");
const imagePreview = document.querySelector("#image-preview");
const imagePreviewImage = document.querySelector("#image-preview-image");
const imagePreviewName = document.querySelector("#image-preview-name");
const attachImageButton = document.querySelector("#attach-image-button");
const removeImageButton = document.querySelector("#remove-image-button");
const reminderForm = document.querySelector("#reminder-form");
const reminderTime = document.querySelector("#reminder-time");
const reminderMessage = document.querySelector("#reminder-message");
const reminderList = document.querySelector("#reminder-list");
const reminderStatus = document.querySelector("#reminder-status");
const reminderContent = document.querySelector("#reminder-content");
const toggleRemindersButton = document.querySelector("#toggle-reminders");
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const SUPPORTED_IMAGE_TYPES = new Set(["image/jpeg", "image/png", "image/gif", "image/webp"]);
let selectedImage = null;
let previewUrl = null;

function addMessage(text, role, imageFile = null) {
    const welcome = conversation.querySelector(".welcome");
    if (welcome) {
        welcome.remove();
    }

    const message = document.createElement("div");
    message.className = `message ${role}`;
    if (text) {
        message.append(document.createTextNode(text));
    }
    if (imageFile) {
        const image = document.createElement("img");
        image.className = "message-image";
        image.alt = imageFile.name || "Attached image";
        const messageImageUrl = URL.createObjectURL(imageFile);
        image.addEventListener("load", () => URL.revokeObjectURL(messageImageUrl), { once: true });
        image.addEventListener("error", () => URL.revokeObjectURL(messageImageUrl), { once: true });
        image.src = messageImageUrl;
        message.append(image);
    }
    conversation.append(message);
    conversation.scrollTop = conversation.scrollHeight;
}

function clearSelectedImage() {
    if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
        previewUrl = null;
    }
    selectedImage = null;
    imageInput.value = "";
    imagePreview.hidden = true;
    imagePreviewImage.removeAttribute("src");
    imagePreviewName.textContent = "";
}

function showImageError(message) {
    status.classList.add("error");
    status.textContent = message;
}

function resizeInput() {
    input.style.height = "auto";
    input.style.height = `${input.scrollHeight}px`;
}

attachImageButton.addEventListener("click", () => imageInput.click());
imageInput.addEventListener("change", () => {
    const image = imageInput.files[0];
    if (!image) {
        return;
    }
    if (!SUPPORTED_IMAGE_TYPES.has(image.type)) {
        clearSelectedImage();
        showImageError("Choose a JPEG, PNG, GIF, or WebP image.");
        return;
    }
    if (image.size > MAX_IMAGE_BYTES) {
        clearSelectedImage();
        showImageError("Images must be 5 MB or smaller.");
        return;
    }
    if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
    }
    selectedImage = image;
    previewUrl = URL.createObjectURL(image);
    imagePreviewImage.src = previewUrl;
    imagePreviewName.textContent = image.name;
    imagePreview.hidden = false;
    status.classList.remove("error");
    status.textContent = "";
});
removeImageButton.addEventListener("click", clearSelectedImage);

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
    const image = selectedImage;
    if ((!message && !image) || sendButton.disabled) {
        return;
    }

    addMessage(message, "user", image);
    input.value = "";
    clearSelectedImage();
    resizeInput();
    sendButton.disabled = true;
    status.classList.remove("error");
    status.textContent = "Minerva is thinking...";

    try {
        let response;
        if (image) {
            const formData = new FormData();
            formData.append("image", image);
            if (message) {
                formData.append("message", message);
            }
            response = await fetch("/image", { method: "POST", body: formData });
        } else {
            response = await fetch("/message", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
                body: new URLSearchParams({ message })
            });
        }
        if (!response.ok) {
            const detail = response.status < 500 ? (await response.text()).trim() : "";
            throw new Error(detail || `Request failed (${response.status})`);
        }

        addMessage(await response.text(), "assistant");
        status.textContent = "";
    } catch (error) {
        status.classList.add("error");
        status.textContent = error instanceof TypeError
            ? "Could not reach Minerva. Please try again."
            : error.message || "The message could not be sent.";
        console.error("Chat request failed", error);
    } finally {
        sendButton.disabled = false;
        input.focus();
    }
});

function setRemindersVisible(visible, persist = true) {
    reminderContent.hidden = !visible;
    toggleRemindersButton.setAttribute("aria-expanded", String(visible));
    toggleRemindersButton.textContent = visible ? "Hide" : "Show";
    if (persist) {
        try {
            window.localStorage.setItem("minerva-reminders-visible", String(visible));
        } catch (error) {
            console.error("Could not save reminder pane visibility", error);
        }
    }
}

let remindersVisible = true;
try {
    remindersVisible = window.localStorage.getItem("minerva-reminders-visible") !== "false";
} catch (error) {
    console.error("Could not load reminder pane visibility", error);
}
setRemindersVisible(remindersVisible, false);
toggleRemindersButton.addEventListener("click", () => {
    setRemindersVisible(reminderContent.hidden);
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
