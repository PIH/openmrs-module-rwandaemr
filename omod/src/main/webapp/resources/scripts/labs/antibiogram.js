/**
 * Antibiogram of a culture order: the organisms isolated (up to three) and their susceptibility (S, I or R) to each
 * antibiotic tested.  Used by the lab results page to enter and validate them, and by the culture results page.
 *
 * An antibiogram is handled here as a list of isolates: [{ organism: conceptUuid, results: { antibioticUuid: interpretationUuid } }]
 * config is the response of GET /ws/rest/v1/rwandaemr/lab/antibiogram/config
 */
class RwandaAntibiogram {

    static get MAX_ISOLATES() { return 3; }

    constructor(jq, config) {
        this.jq = jq;
        this.config = config;
        this.organisms = config.organisms || [];
        this.antibiotics = config.antibiotics || [];
        const order = { S: 0, I: 1, R: 2 };
        this.interpretations = (config.interpretations || []).slice().sort((a, b) => (order[a.code] ?? 9) - (order[b.code] ?? 9));
        this.displays = {};
        this.codes = {};
        this.organisms.concat(this.antibiotics).concat(this.interpretations).forEach(c => { this.displays[c.uuid] = c.display; });
        this.interpretations.forEach(c => { this.codes[c.uuid] = c.code; });
    }

    isAntibiogramTest(conceptUuid) {
        return (this.config.tests || []).includes(conceptUuid);
    }

    /**
     * Converts an antibiogram from GET /ws/rest/v1/rwandaemr/lab/antibiogram into a list of isolates
     */
    static fromRest(restIsolates) {
        return (restIsolates || []).map(i => {
            const results = {};
            (i.results || []).forEach(r => { results[r.antibiotic.uuid] = r.interpretation.uuid; });
            return { organism: i.organism.uuid, results: results };
        });
    }

    static toPayload(orderUuid, encounterUuid, isolates) {
        return {
            order: orderUuid,
            encounter: encounterUuid,
            isolates: isolates.map(i => ({
                organism: i.organism,
                results: Object.keys(i.results).map(antibiotic => ({ antibiotic: antibiotic, interpretation: i.results[antibiotic] }))
            }))
        };
    }

    escape(text) {
        return this.jq("<span>").text(text ?? "").html();
    }

    /**
     * @return html summarising the antibiogram, e.g. "E. coli: 6 S · 1 I · 3 R"
     */
    summaryHtml(isolates) {
        if (!isolates || isolates.length === 0) {
            return '<span class="ab-none">Not entered</span>';
        }
        return isolates.map(i => {
            const counts = { S: 0, I: 0, R: 0 };
            Object.values(i.results).forEach(uuid => { const code = this.codes[uuid]; if (code) counts[code]++; });
            return '<span class="ab-summary-isolate"><em>' + this.escape(this.displays[i.organism]) + '</em>: '
                + counts.S + ' S · ' + counts.I + ' I · ' + counts.R + ' R</span>';
        }).join(" ");
    }

    /**
     * @return a read only view: for each organism, the antibiotics it is sensitive, intermediate and resistant to
     */
    readOnly(isolates) {
        const jq = this.jq;
        const view = jq("<div>").addClass("ab-view");
        if (!isolates || isolates.length === 0) {
            return view.append(jq("<span>").addClass("ab-none").text("No antibiogram"));
        }
        const labels = { S: "Sensitive", I: "Intermediate", R: "Resistant" };
        isolates.forEach(i => {
            const isolate = jq("<div>").addClass("ab-view-isolate");
            isolate.append(jq("<div>").addClass("ab-view-organism").text(this.displays[i.organism] ?? ""));
            ["S", "I", "R"].forEach(code => {
                const names = Object.keys(i.results).filter(a => this.codes[i.results[a]] === code)
                    .map(a => this.displays[a] ?? "").sort((a, b) => a.localeCompare(b));
                if (names.length > 0) {
                    isolate.append(jq("<div>").addClass("ab-view-line ab-view-" + code)
                        .append(jq("<span>").addClass("ab-view-label").text(labels[code] + ": "))
                        .append(jq("<span>").text(names.join(", "))));
                }
            });
            view.append(isolate);
        });
        return view;
    }

    /**
     * Opens a dialog to enter the antibiogram.  onDone is called with the updated isolates when the user confirms.
     */
    openEditor(title, isolates, onDone) {
        const jq = this.jq;
        const max = RwandaAntibiogram.MAX_ISOLATES;
        const overlay = jq("<div>").addClass("ab-overlay");
        const dialog = jq("<div>").addClass("ab-dialog").attr({ role: "dialog", "aria-label": title }).appendTo(overlay);
        dialog.append(jq("<h3>").text(title));

        // The organisms, one column of results each
        const organismSelects = [];
        const germs = jq("<div>").addClass("ab-germs");
        for (let g = 0; g < max; g++) {
            const select = jq("<select>").attr("data-germ", g).append(jq("<option>").attr("value", "").text(""));
            this.organisms.forEach(o => select.append(jq("<option>").attr("value", o.uuid).text(o.display)));
            select.val(isolates[g]?.organism ?? "");
            organismSelects.push(select);
            germs.append(jq("<label>").text("Germ " + (g + 1) + " ").append(select));
        }
        dialog.append(germs);

        const search = jq("<input>").attr({ type: "search", placeholder: "Search antibiotics" }).addClass("ab-search");
        const testedOnly = jq("<input>").attr("type", "checkbox");
        dialog.append(jq("<div>").addClass("ab-tools").append(search)
            .append(jq("<label>").append(testedOnly).append(" Only antibiotics with a result")));

        const table = jq("<table>").addClass("ab-grid");
        const head1 = jq("<tr>").append(jq("<th>").attr("rowspan", 2).text("Antibiotic"));
        const head2 = jq("<tr>");
        for (let g = 0; g < max; g++) {
            head1.append(jq("<th>").addClass("ab-germ-head").attr({ colspan: this.interpretations.length, "data-germ": g }).text("Germ " + (g + 1)));
            this.interpretations.forEach(i => head2.append(jq("<th>").addClass("ab-code ab-code-" + i.code).attr("title", i.display).text(i.code)));
        }
        table.append(jq("<thead>").append(head1).append(head2));
        const body = jq("<tbody>");
        this.antibiotics.forEach(antibiotic => {
            const row = jq("<tr>").attr("data-name", antibiotic.display.toLowerCase()).append(jq("<td>").addClass("ab-name").text(antibiotic.display));
            for (let g = 0; g < max; g++) {
                this.interpretations.forEach(i => {
                    const radio = jq("<input>").attr({
                        type: "radio",
                        name: "ab-" + g + "-" + antibiotic.uuid,
                        value: i.uuid,
                        "data-germ": g,
                        "data-antibiotic": antibiotic.uuid,
                        title: antibiotic.display + ": " + i.display
                    }).prop("checked", isolates[g]?.results?.[antibiotic.uuid] === i.uuid);
                    row.append(jq("<td>").addClass("ab-cell ab-code-" + i.code + (i === this.interpretations[0] ? " ab-germ-start" : "")).append(radio));
                });
            }
            body.append(row);
        });
        table.append(body);
        dialog.append(jq("<div>").addClass("ab-grid-wrapper").append(table));

        const errors = jq("<div>").addClass("ab-errors");
        const cancel = jq("<button>").attr("type", "button").text("Cancel");
        const done = jq("<button>").attr("type", "button").addClass("confirm").text("Done");
        dialog.append(errors).append(jq("<div>").addClass("ab-actions").append(cancel).append(done));

        // A germ's results can only be entered once its organism is chosen
        const applyGermState = () => {
            organismSelects.forEach((select, g) => {
                table.find("input[data-germ='" + g + "']").prop("disabled", !select.val());
                table.find(".ab-germ-head[data-germ='" + g + "']").text(select.val() ? select.find("option:selected").text() : "Germ " + (g + 1));
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
            jq(document).off("keydown.antibiogram");
        };
        cancel.on("click", close);
        jq(document).on("keydown.antibiogram", (event) => { if (event.key === "Escape") close(); });

        done.on("click", () => {
            const problems = [];
            const chosen = organismSelects.map(s => s.val()).filter(v => v);
            if (new Set(chosen).size !== chosen.length) {
                problems.push("The same organism is chosen for two germs");
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
            if (problems.length > 0) {
                errors.empty().append(problems.map(p => jq("<div>").text(p)));
                return;
            }
            close();
            onDone(updated);
        });

        jq("body").append(overlay);
        (organismSelects.find(s => !s.val()) ?? search).focus();
    }
}
