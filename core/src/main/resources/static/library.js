(() => {
    const rssForm = document.getElementById("rss-feed-form");
    const rssStatus = document.getElementById("rss-feed-status");
    const rssSubmit = document.getElementById("rss-feed-submit");
    const mediaForm = document.getElementById("media-candidate-form");
    const mediaStatus = document.getElementById("media-candidate-status");
    const mediaSubmit = document.getElementById("media-candidate-submit");
    const mediaItems = document.getElementById("media-library-items");
    const mediaEmpty = document.getElementById("media-library-empty");
    const mediaListStatus = document.getElementById("media-library-status");

    rssForm.addEventListener("submit", async event => {
        event.preventDefault();
        const parameters = new URLSearchParams(new FormData(rssForm));
        rssSubmit.disabled = true;
        showStatus(rssStatus, "Adding RSS feed…");

        try {
            const response = await fetch(`${rssForm.action}?${parameters.toString()}`, { method: "POST" });
            if (!response.ok) {
                throw new Error(await responseError(response, "Could not add the RSS feed."));
            }
            rssForm.reset();
            showStatus(rssStatus, "RSS feed added successfully.", "success");
        } catch (error) {
            showStatus(rssStatus, errorMessage(error, "Could not add the RSS feed. Please try again."), "error");
        } finally {
            rssSubmit.disabled = false;
        }
    });

    mediaForm.addEventListener("submit", async event => {
        event.preventDefault();
        const url = document.getElementById("media-url").value.trim();
        const hasFile = document.getElementById("media-file").files.length > 0;
        if (Boolean(url) === hasFile) {
            showStatus(mediaStatus, "Enter one public URL or choose one file, not both.", "error");
            return;
        }

        mediaSubmit.disabled = true;
        showStatus(mediaStatus, "Minerva is considering the candidate…");
        try {
            const response = await fetch(mediaForm.action, {
                method: "POST",
                body: new FormData(mediaForm)
            });
            if (!response.ok) {
                throw new Error(await responseError(response, "Could not submit the media candidate."));
            }
            const result = await response.json();
            mediaForm.reset();
            showStatus(mediaStatus,
                result.accepted ? `Added to the library: ${result.reason}` : `Not added: ${result.reason}`,
                result.accepted ? "success" : "");
            await loadMediaItems();
        } catch (error) {
            showStatus(mediaStatus, errorMessage(error, "Could not submit the media candidate. Please try again."), "error");
        } finally {
            mediaSubmit.disabled = false;
        }
    });

    async function loadMediaItems() {
        try {
            const response = await fetch("/api/library");
            if (!response.ok) {
                throw new Error(await responseError(response, "Could not load the media library."));
            }
            const items = await response.json();
            mediaItems.replaceChildren();
            if (items.length === 0) {
                mediaEmpty.textContent = "No media has been selected yet.";
                mediaEmpty.hidden = false;
                return;
            }
            mediaEmpty.hidden = true;
            for (const item of items) {
                mediaItems.append(createMediaRow(item));
            }
        } catch (error) {
            mediaEmpty.hidden = true;
            showStatus(mediaListStatus, errorMessage(error, "Could not load the media library."), "error");
        }
    }

    function createMediaRow(item) {
        const section = document.createElement("section");
        section.className = "library-item";
        const heading = document.createElement("h3");
        heading.textContent = item.title;
        section.append(heading);

        const details = document.createElement("p");
        details.className = "hint";
        details.textContent = `${item.contentType} · Added ${new Date(item.storedAt).toLocaleString()} · ${item.curationReason}`;
        section.append(details);

        if (item.source) {
            const source = document.createElement("a");
            source.href = item.source;
            source.target = "_blank";
            source.rel = "noopener noreferrer";
            source.textContent = "Original source";
            section.append(source, document.createTextNode(" · "));
        }
        const content = document.createElement("a");
        content.href = `/api/library/${encodeURIComponent(item.id)}/content`;
        content.textContent = "Download saved media";
        section.append(content, document.createTextNode(" · "));

        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "secondary";
        remove.textContent = "Remove";
        remove.addEventListener("click", async () => {
            remove.disabled = true;
            try {
                const response = await fetch(`/api/library/${encodeURIComponent(item.id)}`, { method: "DELETE" });
                if (!response.ok) {
                    throw new Error(await responseError(response, "Could not remove the media item."));
                }
                showStatus(mediaListStatus, "Media item removed.", "success");
                await loadMediaItems();
            } catch (error) {
                showStatus(mediaListStatus, errorMessage(error, "Could not remove the media item."), "error");
                remove.disabled = false;
            }
        });
        section.append(remove);
        return section;
    }

    async function responseError(response, fallback) {
        const responseBody = await response.text();
        if (responseBody) {
            try {
                const body = JSON.parse(responseBody);
                    if (body && typeof body.error === "string") {
                        return body.error;
                    }
            } catch {
                return responseBody;
            }
        }
        return `${fallback} (HTTP ${response.status}).`;
    }

    function showStatus(element, message, kind = "") {
        element.hidden = false;
        element.className = `notice ${kind}`.trim();
        element.textContent = message;
    }

    function errorMessage(error, fallback) {
        return error instanceof Error ? error.message : fallback;
    }

    loadMediaItems();
})();
