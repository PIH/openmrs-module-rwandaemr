(function() {
    var targetLabel = "rhip reception number";
    var excludedNames = {};
    var patched = false;

    function getJq() {
        return window.jq || window.jQuery;
    }

    function normalize(text) {
        return (text || "").replace(/\s+/g, " ").replace(/:\s*$/, "").trim().toLowerCase();
    }

    function rememberFieldNames(row, $) {
        $(row).find(":input[name]").each(function() {
            excludedNames[$(this).attr("name")] = true;
        });
    }

    function removeRhipReceptionAttribute() {
        var $ = getJq();
        if (!$) {
            return;
        }

        $("#quick-visit-creation-dialog tr").each(function() {
            var labelText = normalize($(this).find("td.info, label").first().text());
            if (labelText === targetLabel) {
                rememberFieldNames(this, $);
                $(this).remove();
            }
        });
    }

    function patchVisitParamBuilder() {
        if (patched || !window.visit || !window.visit.buildVisitTypeAttributeParams) {
            return;
        }

        var originalBuildVisitTypeAttributeParams = window.visit.buildVisitTypeAttributeParams;
        window.visit.buildVisitTypeAttributeParams = function() {
            removeRhipReceptionAttribute();
            var params = originalBuildVisitTypeAttributeParams.apply(this, arguments);
            Object.keys(excludedNames).forEach(function(name) {
                delete params[name];
            });
            return params;
        };
        patched = true;
    }

    function initialize() {
        removeRhipReceptionAttribute();
        patchVisitParamBuilder();
    }

    var attempts = 0;
    var interval = window.setInterval(function() {
        initialize();
        attempts++;
        if (patched || attempts > 20) {
            window.clearInterval(interval);
        }
    }, 250);

    var $ = getJq();
    if ($) {
        $(initialize);
    }
})();
