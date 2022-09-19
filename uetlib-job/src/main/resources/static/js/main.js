function refreshPage(page) {
    const url = urlWithoutParams();
    const params = urlParams();

    params.delete("page");
    if (page || page === 0) {
        params.append("page", page);
    }

    window.open(toUrl(url, params), "_self");
}

$("#prev-page").click(function () {
    refreshPage(getCurrentPage() - 1);
});

$("#current-page").click(function () {
    refreshPage(getCurrentPage());
});

$("#next-page").click(function () {
    refreshPage(getCurrentPage() + 1);
});

$(".startJob").click(function (e) {
    e.preventDefault();
    let url = e.target.href;
    $("#forceRun").attr("href", url + "?forceRun=true");
    $("#skipIfUnchanged").attr("href", url + "?forceRun=false");

    let splitUrl = url.split("/");
    let jobClass = splitUrl[splitUrl.length - 2];
    let jobClassSplit = jobClass.split(".");
    let jobName = jobClassSplit[jobClassSplit.length - 1];
    $("#startNowModalTitle").html(jobName);
});

$(".logLink").click(function (e) {
    e.preventDefault();
    const leftPos = screen.width - 1000;
    window.open(this.href, "Log Output",
        "height=1000 ,width=1500, top=0, left=" + leftPos);
    return false;
});

const getCurrentPage = () => parseInt($("#current-page").attr("data-page"));

const urlWithoutParams = () => window.location.protocol + '//'
    + window.location.host + window.location.pathname;
const urlParams = () => new URLSearchParams(window.location.search);
const toUrl = (url, params) => `${url}?${params.toString()}`;

const updateParam = (newValue, paramName) => {
    const url = urlWithoutParams();
    const params = urlParams();

    const oldValue = params.get(paramName);
    params.delete(paramName);
    if ("-" !== newValue) {
        if (newValue !== oldValue) {
            params.delete("page");
        }
        params.append(paramName, newValue);
    }

    window.location.href = toUrl(url, params);
};

$('#jobFilter').change(function () {
    updateParam($(this).val(), "name");
});

$('#stateFilter').change(function () {
    updateParam($(this).val(), "state");
});

function showToast(headerStrong, body) {
    let now = Date.now();

    if (Array.isArray(body)) {
        body = body.join("<br>");
    }

    var toastContainer = document.getElementById('toast-container');
    let toastHtml = '<div class="toast" role="alert" aria-live="assertive" aria-atomic="true"'
        + '               id="toast-' + now + '" data-bs-delay="6000">'
        + '        <div class="toast-header">'
        + '            <strong class="me-auto">' + headerStrong + '</strong>'
        + '            <small class="text-muted">'
        + new Date(now).toLocaleTimeString()
        + '            </small>'
        + '            <button type="button" class="btn-close" data-bs-dismiss="toast"'
        + '                    aria-label="Close"></button>'
        + '        </div>'
        + '        <div class="toast-body">' + body + '</div>'
        + '    </div>';
    toastContainer.insertAdjacentHTML('beforeend', toastHtml);

    var toastElement = document.getElementById('toast-' + now);
    var toast = new bootstrap.Toast(toastElement);
    toast.show();
}