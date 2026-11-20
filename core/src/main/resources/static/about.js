const aboutPage = document.querySelector("[data-about-source-digest]");

async function checkAboutSources() {
    try {
        const response = await fetch("/api/about");
        if (!response.ok) {
            throw new Error(`Request failed (${response.status})`);
        }
        const latest = await response.json();
        if (!latest.refreshPending && latest.biographySourceDigest !== aboutPage.dataset.aboutSourceDigest) {
            window.location.reload();
        }
    } catch (error) {
        console.error("Could not check for About page updates", error);
    }
}

window.setInterval(() => {
    if (aboutPage && !document.hidden) {
        void checkAboutSources();
    }
}, 60_000);
