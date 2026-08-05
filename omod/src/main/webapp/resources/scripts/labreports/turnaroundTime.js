(function() {
    var contextPath = '/' + OPENMRS_CONTEXT_PATH;

    function today() {
        return formatDate(new Date());
    }

    function firstDayOfMonth() {
        var date = new Date();
        date.setDate(1);
        return formatDate(date);
    }

    function formatDate(date) {
        var day = pad2(date.getDate());
        var month = pad2(date.getMonth() + 1);
        return day + '/' + month + '/' + date.getFullYear();
    }

    function pad2(value) {
        return value < 10 ? '0' + value : '' + value;
    }

    function params() {
        var values = {};
        values.startDate = document.getElementById('startDate').value;
        values.endDate = document.getElementById('endDate').value;
        values.locationId = document.getElementById('locationId').value;
        values.categoryConceptId = document.getElementById('categoryConceptId').value;
        values.testConceptId = document.getElementById('testConceptId').value;
        values.targetHours = document.getElementById('targetHours').value;

        return Object.keys(values).filter(function(key) {
            return values[key] !== null && values[key] !== '';
        }).map(function(key) {
            return encodeURIComponent(key) + '=' + encodeURIComponent(values[key]);
        }).join('&');
    }

    function loadReport() {
        if (!document.getElementById('startDate').value || !document.getElementById('endDate').value) {
            setMessage('Start date and end date are required.', 'error');
            return;
        }
        if (!isValidDateText(document.getElementById('startDate').value) ||
                !isValidDateText(document.getElementById('endDate').value)) {
            setMessage('Dates must use dd/MM/yyyy.', 'error');
            return;
        }
        setLoading(true);
        window.setTimeout(function() {
            Promise.all([
                fetchJson(contextPath + '/ws/rest/v1/rwandaemr/labReports/tat/summary?' + params()),
                fetchJson(contextPath + '/ws/rest/v1/rwandaemr/labReports/tat/details?' + params())
            ]).then(function(responses) {
                renderSummary(responses[0]);
                renderRows(responses[1].results || []);
                renderChart(responses[1].results || []);
                setMessage((responses[1].results || []).length + ' order(s)', '');
            }).catch(function(error) {
                document.getElementById('tatRows').innerHTML = '<tr><td colspan="9">Unable to load TaT data.</td></tr>';
                document.getElementById('testChart').textContent = 'Unable to load chart data.';
                setMessage(error.message || 'Unable to load TaT data.', 'error');
            }).then(function() {
                setLoading(false);
            });
        }, 0);
    }

    function fetchJson(url) {
        return fetch(url, { credentials: 'same-origin' }).then(function(response) {
            if (!response.ok) {
                throw new Error('Unable to load TaT data. Server returned ' + response.status + '.');
            }
            return response.json();
        });
    }

    function setLoading(isLoading) {
        var runButton = document.getElementById('runReport');
        runButton.disabled = isLoading;
        runButton.textContent = isLoading ? 'Running...' : 'Run';
        document.getElementById('tatRows').setAttribute('aria-busy', isLoading ? 'true' : 'false');
        if (isLoading) {
            setMessage('Pulling laboratory turnaround time data. Please wait...', 'loading');
            document.getElementById('tatRows').innerHTML = '<tr class="loading-row"><td colspan="9">Pulling data...</td></tr>';
            document.getElementById('testChart').textContent = 'Preparing chart...';
            resetSummary();
        }
    }

    function setMessage(text, status) {
        var message = document.getElementById('message');
        message.textContent = text;
        message.className = status || '';
    }

    function resetSummary() {
        setText('totalOrders', '-');
        setText('completedOrders', '-');
        setText('medianHours', '-');
        setText('p90Hours', '-');
        setText('withinTarget', '-');
        setText('delayedOrders', '-');
    }

    function renderSummary(summary) {
        setText('totalOrders', summary.totalOrders);
        setText('completedOrders', summary.completedOrders);
        setText('medianHours', hours(summary.medianHours));
        setText('p90Hours', hours(summary.percentile90Hours));
        setText('withinTarget', summary.percentWithinTarget == null ? '-' : summary.percentWithinTarget + '%');
        setText('delayedOrders', summary.delayedOrders);
    }

    function renderRows(rows) {
        var html = rows.map(function(row) {
            return '<tr>' +
                '<td>' + safe(row.orderedAt) + '</td>' +
                '<td>' + escapeHtml(row.test) + '</td>' +
                '<td>' + escapeHtml(row.location) + '</td>' +
                '<td>' + escapeHtml(row.fulfillerStatus) + '</td>' +
                '<td>' + safe(row.specimenReceivedAt) + '</td>' +
                '<td>' + safe(row.resultedAt) + '</td>' +
                '<td class="numeric">' + hours(row.orderToReceivedHours) + '</td>' +
                '<td class="numeric">' + hours(row.receivedToResultHours) + '</td>' +
                '<td class="numeric">' + hours(row.orderToResultHours) + '</td>' +
                '</tr>';
        }).join('');
        document.getElementById('tatRows').innerHTML = html || '<tr><td colspan="9">No results found.</td></tr>';
    }

    function renderChart(rows) {
        var valuesByTest = {};
        rows.forEach(function(row) {
            if (row.orderToResultHours != null) {
                valuesByTest[row.test] = valuesByTest[row.test] || [];
                valuesByTest[row.test].push(row.orderToResultHours);
            }
        });
        var chartRows = Object.keys(valuesByTest).map(function(test) {
            var values = valuesByTest[test].sort(function(a, b) { return a - b; });
            var row = {};
            row.test = test;
            row.median = median(values);
            return row;
        }).sort(function(a, b) { return b.median - a.median; }).slice(0, 12);
        var max = chartRows.reduce(function(current, row) { return Math.max(current, row.median); }, 1);
        document.getElementById('testChart').innerHTML = chartRows.map(function(row) {
            var width = Math.max(2, Math.round((row.median / max) * 100));
            return '<div class="tat-bar">' +
                '<div>' + escapeHtml(row.test) + '</div>' +
                '<div><div class="tat-bar-fill" style="width:' + width + '%"></div></div>' +
                '<div class="numeric">' + hours(row.median) + '</div>' +
                '</div>';
        }).join('') || 'No completed results to chart.';
    }

    function updateTestOptions() {
        var category = document.getElementById('categoryConceptId').value;
        Array.prototype.forEach.call(document.querySelectorAll('#testConceptId option[data-category]'), function(option) {
            option.hidden = category && option.getAttribute('data-category') !== category;
        });
        var selected = document.querySelector('#testConceptId option:checked');
        if (selected && selected.hidden) {
            document.getElementById('testConceptId').value = '';
        }
    }

    function median(values) {
        if (!values.length) {
            return null;
        }
        var middle = Math.floor(values.length / 2);
        return values.length % 2 ? values[middle] : ((values[middle - 1] + values[middle]) / 2);
    }

    function isValidDateText(value) {
        if (!value || value.length !== 10) {
            return false;
        }
        if (value.charAt(2) !== '/' || value.charAt(5) !== '/') {
            return false;
        }
        var day = value.substring(0, 2);
        var month = value.substring(3, 5);
        var year = value.substring(6, 10);
        return isDigits(day) && isDigits(month) && isDigits(year);
    }

    function isDigits(value) {
        for (var i = 0; i < value.length; i++) {
            var code = value.charCodeAt(i);
            if (code < 48 || code > 57) {
                return false;
            }
        }
        return true;
    }

    function setText(id, value) {
        document.getElementById(id).textContent = value == null ? '-' : value;
    }

    function hours(value) {
        return value == null ? '-' : value + 'h';
    }

    function safe(value) {
        return value || '-';
    }

    function escapeHtml(value) {
        return (value || '').replace(/[&<>"']/g, function(ch) {
            return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[ch];
        });
    }

    document.addEventListener('DOMContentLoaded', function() {
        document.getElementById('startDate').value = firstDayOfMonth();
        document.getElementById('endDate').value = today();
        document.getElementById('runReport').addEventListener('click', loadReport);
        document.getElementById('categoryConceptId').addEventListener('change', updateTestOptions);
        updateTestOptions();
        loadReport();
    });
})();
