const customizationOptions = {
    palette: new Set(["forest", "ocean", "rose", "midnight"]),
    layout: new Set(["compact", "comfortable", "wide"]),
    typography: new Set(["system", "serif", "mono"])
};

async function applyMinervaCustomization() {
    try {
        const response = await fetch("/api/customization");
        if (!response.ok) {
            throw new Error(`Request failed (${response.status})`);
        }
        const settings = await response.json();
        for (const [name, allowed] of Object.entries(customizationOptions)) {
            if (!allowed.has(settings[name])) {
                throw new Error(`Unsupported ${name} value from the customization endpoint.`);
            }
            document.body.dataset[name] = settings[name];
        }
    } catch (error) {
        console.error("Could not apply Minerva's saved appearance settings", error);
    }
}

window.applyMinervaCustomization = applyMinervaCustomization;
void applyMinervaCustomization();
window.setInterval(() => {
    if (!document.hidden) {
        void applyMinervaCustomization();
    }
}, 60_000);
