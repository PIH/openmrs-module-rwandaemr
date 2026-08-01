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
        values.resultType = document.getElementById('resultType').value;

        return Object.keys(values).filter(function(key) {
            return values[key] !== null && values[key] !== '';
        }).map(function(key) {
            return encodeURIComponent(key) + '=' + encodeURIComponent(values[key]);
        }).join('&');
    }

    function validateDates() {
        if (!document.getElementById('startDate').value || !document.getElementById('endDate').value) {
            setMessage('Start date and end date are required.', 'error');
            return false;
        }
        if (!isValidDateText(document.getElementById('startDate').value) ||
                !isValidDateText(document.getElementById('endDate').value)) {
            setMessage('Dates must use dd/MM/yyyy.', 'error');
            return false;
        }
        return true;
    }

    function loadReport() {
        if (!validateDates()) {
            return;
        }
        setLoading(true);
        window.setTimeout(function() {
            fetch(contextPath + '/ws/rest/v1/rwandaemr/labReports/aggregate?' + params(), { credentials: 'same-origin' })
                .then(function(response) {
                    if (!response.ok) {
                        throw new Error('Unable to load report. Server returned ' + response.status + '.');
                    }
                    return response.json();
                })
                .then(function(data) {
                    var rows = data.results || [];
                    var html = rows.map(function(row) {
                        return '<tr>' +
                            '<td>' + escapeHtml(row.category) + '</td>' +
                            '<td>' + escapeHtml(row.test) + '</td>' +
                            '<td class="numeric">' + number(row.positiveCount) + '</td>' +
                            '<td class="numeric">' + number(row.negativeCount) + '</td>' +
                            '<td class="numeric">' + number(row.totalResultedCount) + '</td>' +
                            '</tr>';
                    }).join('');
                    document.getElementById('reportRows').innerHTML = html || '<tr><td colspan="5">No results found.</td></tr>';
                    setMessage(rows.length + ' row(s)', '');
                })
                .catch(function(error) {
                    document.getElementById('reportRows').innerHTML = '<tr><td colspan="5">Unable to load report.</td></tr>';
                    setMessage(error.message || 'Unable to load report.', 'error');
                })
                .then(function() {
                    setLoading(false);
                });
        }, 0);
    }

    function setLoading(isLoading) {
        var runButton = document.getElementById('runReport');
        var exportButton = document.getElementById('exportReport');
        runButton.disabled = isLoading;
        exportButton.disabled = isLoading;
        runButton.textContent = isLoading ? 'Running...' : 'Run';
        document.getElementById('reportRows').setAttribute('aria-busy', isLoading ? 'true' : 'false');
        if (isLoading) {
            setMessage('Pulling aggregate lab data. Please wait...', 'loading');
            document.getElementById('reportRows').innerHTML = '<tr class="loading-row"><td colspan="5">Pulling data...</td></tr>';
        }
    }

    function setMessage(text, status) {
        var message = document.getElementById('message');
        message.textContent = text;
        message.className = 'lab-report-summary' + (status ? ' ' + status : '');
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

    function escapeHtml(value) {
        return (value || '').replace(/[&<>"']/g, function(ch) {
            return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[ch];
        });
    }

    function number(value) {
        return value == null ? '0' : value;
    }

    document.addEventListener('DOMContentLoaded', function() {
        document.getElementById('startDate').value = firstDayOfMonth();
        document.getElementById('endDate').value = today();
        document.getElementById('runReport').addEventListener('click', loadReport);
        document.getElementById('exportReport').addEventListener('click', function() {
            if (validateDates()) {
                window.location = contextPath + '/ws/rest/v1/rwandaemr/labReports/aggregate/export?' + params();
            }
        });
        document.getElementById('categoryConceptId').addEventListener('change', updateTestOptions);
        updateTestOptions();
        loadReport();
    });
})();
