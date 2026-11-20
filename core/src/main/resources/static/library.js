(() => {
    const form = document.getElementById("rss-feed-form");
    const status = document.getElementById("rss-feed-status");
    const submitButton = document.getElementById("rss-feed-submit");

    form.addEventListener("submit", async event => {
        event.preventDefault();
        const parameters = new URLSearchParams(new FormData(form));
        submitButton.disabled = true;
        status.hidden = false;
        status.className = "notice";
        status.textContent = "Adding RSS feed…";

        try {
            const response = await fetch(`${form.action}?${parameters.toString()}`, { method: "POST" });
            if (!response.ok) {
                const errorMessage = await response.text();
                throw new Error(errorMessage || `Could not add the RSS feed (HTTP ${response.status}).`);
            }
            form.reset();
            status.className = "notice success";
            status.textContent = "RSS feed added successfully.";
        } catch (error) {
            status.className = "notice error";
            status.textContent = error instanceof Error
                ? error.message
                : "Could not add the RSS feed. Please try again.";
        } finally {
            submitButton.disabled = false;
        }
    });
})();
