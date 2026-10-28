/**
 * The antimicrobial susceptibility of a bacteriology exam, which the lab results page enters with its own widget: the
 * organisms isolated (up to three) and their susceptibility (S, I or R) to each antibiotic tested.
 *
 * The susceptibility of an order is handled here as:
 *   { isolates: [{ organism: uuid, results: { antibioticUuid: interpretationUuid } }] }
 * config is the response of GET /ws/rest/v1/rwandaemr/lab/bacteriology/config
 */
class RwandaBacteriology {

    static get MAX_ISOLATES() { return 3; }

    constructor(jq, config) {
        this.jq = jq;
        this.config = config || {};
        this.susceptibility = this.config.susceptibility || {};
        this.organisms = this.susceptibility.organisms || [];
        this.antibiotics = this.susceptibility.antibiotics || [];
        const order = { S: 0, I: 1, R: 2 };
        this.interpretations = (this.susceptibility.interpretations || []).slice().sort((a, b) => (order[a.code] ?? 9) - (order[b.code] ?? 9));
        this.displays = {};
        this.codes = {};
        this.organisms.concat(this.antibiotics).concat(this.interpretations).forEach(c => { this.displays[c.uuid] = c.display; });
        this.interpretations.forEach(c => { this.codes[c.uuid] = c.code; });
    }

    /**
     * @return the key of the results entered with a widget of this script for the member concept of an exam, or null
     */
    keyOf(conceptUuid) {
        return conceptUuid && this.susceptibility.uuid === conceptUuid ? "isolates" : null;
    }

    hasOwnWidget(conceptUuid) {
        return this.keyOf(conceptUuid) !== null;
    }

    /**
     * Converts the results of an order from GET /ws/rest/v1/rwandaemr/lab/bacteriology
     */
    static fromRest(rest) {
        return {
            isolates: (rest?.isolates || []).map(i => {
                const results = {};
                (i.results || []).forEach(r => { results[r.antibiotic.uuid] = r.interpretation.uuid; });
                return { organism: i.organism.uuid, results: results };
            })
        };
    }

    /**
     * @return the payload of POST /ws/rest/v1/rwandaemr/lab/bacteriology, with the results by key that were changed
     */
    static toPayload(orderUuid, encounterUuid, changes) {
        const payload = { order: orderUuid, encounter: encounterUuid };
        if (changes.isolates) {
            payload.isolates = changes.isolates.map(i => ({
                organism: i.organism,
                results: Object.keys(i.results).map(antibiotic => ({ antibiotic: antibiotic, interpretation: i.results[antibiotic] }))
            }));
        }
        return payload;
    }

    escape(text) {
        return this.jq("<span>").text(text ?? "").html();
    }

    /**
     * @return html summarising the susceptibility, e.g. "E. coli: 6 S · 1 I · 3 R"
     */
    summaryHtml(isolates) {
        if (!isolates || isolates.length === 0) {
            return '<span class="bact-none">Not entered</span>';
        }
        return isolates.map(i => {
            const counts = { S: 0, I: 0, R: 0 };
            Object.values(i.results).forEach(uuid => { const code = this.codes[uuid]; if (code) counts[code]++; });
            return '<span class="bact-summary-isolate"><em>' + this.escape(this.displays[i.organism]) + '</em>: '
                + counts.S + ' S · ' + counts.I + ' I · ' + counts.R + ' R</span>';
        }).join(" ");
    }

    /**
     * @return a read only view: for each organism, the antibiotics it is sensitive, intermediate and resistant to
     */
    susceptibilityReadOnly(isolates) {
        const jq = this.jq;
        const view = jq("<div>").addClass("bact-view");
        if (!isolates || isolates.length === 0) {
            return view.append(jq("<span>").addClass("bact-none").text("No susceptibility results"));
        }
        const labels = { S: "Sensitive", I: "Intermediate", R: "Resistant" };
        isolates.forEach(i => {
            const isolate = jq("<div>").addClass("bact-view-isolate");
            isolate.append(jq("<div>").addClass("bact-view-organism").text(this.displays[i.organism] ?? ""));
            ["S", "I", "R"].forEach(code => {
                const names = Object.keys(i.results).filter(a => this.codes[i.results[a]] === code)
                    .map(a => this.displays[a] ?? "").sort((a, b) => a.localeCompare(b));
                if (names.length > 0) {
                    isolate.append(jq("<div>").addClass("bact-view-line bact-view-" + code)
                        .append(jq("<span>").addClass("bact-view-label").text(labels[code] + ": "))
                        .append(jq("<span>").text(names.join(", "))));
                }
            });
            view.append(isolate);
        });
        return view;
    }

    /**
     * @return a table of the antibiotics tested, one column per organism with S, I or R, for the clinicians
     */
    susceptibilityTable(isolates) {
        const jq = this.jq;
        const labels = { S: "Sensitive", I: "Intermediate", R: "Resistant" };
        const antibiotics = [...new Set(isolates.flatMap(i => Object.keys(i.results)))]
            .sort((a, b) => (this.displays[a] ?? "").localeCompare(this.displays[b] ?? ""));
        const head = jq("<tr>").append(jq("<th>").text("Antibiotic"));
        isolates.forEach(i => head.append(jq("<th>").addClass("bact-table-organism").text(this.displays[i.organism] ?? "")));
        const body = jq("<tbody>");
        antibiotics.forEach(antibiotic => {
            const row = jq("<tr>").append(jq("<td>").text(this.displays[antibiotic] ?? ""));
            isolates.forEach(i => {
                const code = this.codes[i.results[antibiotic]];
                row.append(jq("<td>").addClass("bact-table-result" + (code ? " bact-code-" + code : "")).attr("title", labels[code] ?? "").text(code ?? ""));
            });
            body.append(row);
        });
        const table = jq("<table>").addClass("bact-table").append(jq("<thead>").append(head)).append(body);
        const legend = jq("<div>").addClass("bact-legend").html('<span class="bact-code-S">S</span> Sensitive · '
            + '<span class="bact-code-I">I</span> Intermediate · <span class="bact-code-R">R</span> Resistant');
        return jq("<div>").append(table).append(legend);
    }

    /**
     * Opens a dialog to enter the susceptibility.  onDone is called with the updated isolates when the user confirms.
     */
    openSusceptibilityEditor(title, isolates, onDone) {
        const jq = this.jq;
        const max = RwandaBacteriology.MAX_ISOLATES;
        const overlay = jq("<div>").addClass("bact-overlay");
        const dialog = jq("<div>").addClass("bact-dialog").attr({ role: "dialog", "aria-label": title }).appendTo(overlay);
        dialog.append(jq("<h3>").text(title));

        // The organisms, one column of results each
        const organismSelects = [];
        const germs = jq("<div>").addClass("bact-germs");
        for (let g = 0; g < max; g++) {
            const select = jq("<select>").attr("data-germ", g).append(jq("<option>").attr("value", "").text(""));
            this.organisms.forEach(o => select.append(jq("<option>").attr("value", o.uuid).text(o.display)));
            select.val(isolates[g]?.organism ?? "");
            organismSelects.push(select);
            germs.append(jq("<label>").text("Germ " + (g + 1) + " ").append(select));
        }
        dialog.append(germs);

        const search = jq("<input>").attr({ type: "search", placeholder: "Search antibiotics" }).addClass("bact-search");
        const testedOnly = jq("<input>").attr("type", "checkbox");
        dialog.append(jq("<div>").addClass("bact-tools").append(search)
            .append(jq("<label>").append(testedOnly).append(" Only antibiotics with a result")));

        const table = jq("<table>").addClass("bact-grid");
        const head1 = jq("<tr>").append(jq("<th>").attr("rowspan", 2).text("Antibiotic"));
        const head2 = jq("<tr>");
        for (let g = 0; g < max; g++) {
            head1.append(jq("<th>").addClass("bact-germ-head").attr({ colspan: this.interpretations.length, "data-germ": g }).text("Germ " + (g + 1)));
            this.interpretations.forEach(i => head2.append(jq("<th>").addClass("bact-code bact-code-" + i.code).attr("title", i.display).text(i.code)));
        }
        table.append(jq("<thead>").append(head1).append(head2));
        const body = jq("<tbody>");
        this.antibiotics.forEach(antibiotic => {
            const row = jq("<tr>").attr("data-name", antibiotic.display.toLowerCase()).append(jq("<td>").addClass("bact-name").text(antibiotic.display));
            for (let g = 0; g < max; g++) {
                this.interpretations.forEach(i => {
                    const radio = jq("<input>").attr({
                        type: "radio",
                        name: "bact-" + g + "-" + antibiotic.uuid,
                        value: i.uuid,
                        "data-germ": g,
                        "data-antibiotic": antibiotic.uuid,
                        title: antibiotic.display + ": " + i.display
                    }).prop("checked", isolates[g]?.results?.[antibiotic.uuid] === i.uuid);
                    row.append(jq("<td>").addClass("bact-cell bact-code-" + i.code + (i === this.interpretations[0] ? " bact-germ-start" : "")).append(radio));
                });
            }
            body.append(row);
        });
        table.append(body);
        dialog.append(jq("<div>").addClass("bact-grid-wrapper").append(table));

        const errors = jq("<div>").addClass("bact-errors");
        const cancel = jq("<button>").attr("type", "button").text("Cancel");
        const done = jq("<button>").attr("type", "button").addClass("confirm").text("Done");
        dialog.append(errors).append(jq("<div>").addClass("bact-actions").append(cancel).append(done));

        // A germ's results can only be entered once its organism is chosen
        const applyGermState = () => {
            organismSelects.forEach((select, g) => {
                table.find("input[data-germ='" + g + "']").prop("disabled", !select.val());
                table.find(".bact-germ-head[data-germ='" + g + "']").text(select.val() ? select.find("option:selected").text() : "Germ " + (g + 1));
            });
        };
        organismSelects.forEach(select => select.on("change", applyGermState));
        applyGermState();

        // Clicking a chosen result again clears it
        table.on("mousedown", "input[type=radio]", function() { jq(this).data("wasChecked", this.checked); });
        table.on("click", "input[type=radio]", function() {
            if (jq(this).data("wasChecked")) {
                this.checked = false;
            }
        });

        const filter = () => {
            const text = search.val().trim().toLowerCase();
            body.find("tr").each(function() {
                const row = jq(this);
                const matches = !text || row.attr("data-name").includes(text);
                const tested = !testedOnly.prop("checked") || row.find("input:checked").length > 0;
                row.toggle(matches && tested);
            });
        };
        search.on("input", filter);
        testedOnly.on("change", filter);

        const close = () => {
            overlay.remove();
            jq(document).off("keydown.bacteriology");
        };
        cancel.on("click", close);
        jq(document).on("keydown.bacteriology", (event) => { if (event.key === "Escape") close(); });

        done.on("click", () => {
            const chosen = organismSelects.map(s => s.val()).filter(v => v);
            if (new Set(chosen).size !== chosen.length) {
                errors.empty().append(jq("<div>").text("The same organism is chosen for two germs"));
                return;
            }
            const updated = [];
            organismSelects.forEach((select, g) => {
                const results = {};
                table.find("input[data-germ='" + g + "']:checked").each(function() {
                    results[jq(this).attr("data-antibiotic")] = jq(this).val();
                });
                if (select.val()) {
                    updated.push({ organism: select.val(), results: results });
                }
            });
            close();
            onDone(updated);
        });

        jq("body").append(overlay);
        (organismSelects.find(s => !s.val()) ?? search).focus();
    }
}

/**
 * Renders the antimicrobial susceptibility of a bacteriology exam on the pihapps patient lab results page, as a table:
 * see the org.openmrs.module.pihapps.patientLabResults.obsGroupRenderers extension of the distribution.
 * obs is the antimicrobial susceptibility obs group, with its order.
 */
function rwandaRenderSusceptibility(obs) {
    const rest = openmrsContextPath + "/ws/rest/v1/rwandaemr/lab/bacteriology";
    const getJson = (url) => new Promise((resolve, reject) => jq.getJSON(url).done(resolve).fail(reject));
    RwandaBacteriology.configLoad = RwandaBacteriology.configLoad ?? getJson(rest + "/config");
    return Promise.all([RwandaBacteriology.configLoad, getJson(rest + "?" + jq.param({ order: obs.order.uuid }))]).then(([config, results]) => {
        const isolates = RwandaBacteriology.fromRest(results[obs.order.uuid]).isolates;
        return new RwandaBacteriology(jq, config).susceptibilityTable(isolates);
    });
}
