(() => {
    const storageKey = "minerva-personality-import-jobs";
    const jobsContainer = document.getElementById("personality-import-jobs");
    if (!jobsContainer) {
        return;
    }

    function savedJobIds() {
        try {
            const value = JSON.parse(localStorage.getItem(storageKey) || "[]");
            return Array.isArray(value) ? value.filter(id => typeof id === "string") : [];
        } catch {
            return [];
        }
    }

    function rememberJob(id) {
        const ids = savedJobIds().filter(savedId => savedId !== id);
        ids.push(id);
        localStorage.setItem(storageKey, JSON.stringify(ids.slice(-10)));
    }

    function statusElement(id) {
        const element = document.createElement("p");
        element.className = "notice";
        element.setAttribute("role", "status");
        element.dataset.jobId = id;
        jobsContainer.prepend(element);
        return element;
    }

    async function refreshJob(id, status) {
        try {
            const response = await fetch(`/api/personality/imports/${encodeURIComponent(id)}`);
            const job = await response.json();
            if (!response.ok) {
                const message = job.error || `Could not retrieve import ${id}.`;
                if (response.status < 500) {
                    status.className = "notice error";
                    status.textContent = `Import ${id}: ${message}`;
                    return;
                }
                throw new Error(message);
            }
            const failures = Array.isArray(job.failures)
                ? job.failures.map(failure => `${failure.filename}: ${failure.message}`).join("; ")
                : "";
            const counts = `Processed ${job.successfulFileCount} file(s), saved ${job.savedTagCount} interest(s), and analysed ${job.analysedMediaCount} media item(s).`;
            status.className = job.status === "FAILED" || failures ? "notice error" : "notice success";
            status.textContent = `Import ${id}: ${job.status}: ${counts}${job.error ? ` ${job.error}` : ""}${failures ? ` Failures: ${failures}` : ""}`;
            if (job.status === "QUEUED" || job.status === "RUNNING") {
                window.setTimeout(() => refreshJob(id, status), 1500);
            }
        } catch (error) {
            status.className = "notice error";
            status.textContent = `${error.message} Retrying in a few seconds.`;
            window.setTimeout(() => refreshJob(id, status), 5000);
        }
    }

    for (const id of savedJobIds()) {
        refreshJob(id, statusElement(id));
    }

    for (const form of document.querySelectorAll("[data-personality-import-form]")) {
        form.addEventListener("submit", async event => {
            event.preventDefault();
            const status = statusElement("pending");
            status.textContent = "Uploading files and queueing the import…";
            try {
                const response = await fetch(form.action, {
                    method: "POST",
                    body: new FormData(form)
                });
                const job = await response.json();
                if (!response.ok) {
                    throw new Error(job.error || "Could not queue the personality import.");
                }
                status.dataset.jobId = job.id;
                rememberJob(job.id);
                refreshJob(job.id, status);
            } catch (error) {
                status.className = "notice error";
                status.textContent = error.message;
            }
        });
    }
})();
