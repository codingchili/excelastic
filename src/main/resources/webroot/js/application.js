let websock = new WebSocket("ws://" + location.host + "/");
let uploadId = '';

websock.onmessage = (e) => {
    let data = JSON.parse(e.data);
    console.log(e.data);

    if (data.uploadId === uploadId) {

        if (data.action === "import") {
            $('#panelheader').text('Importing ' + $('#file').val().split('\\').pop() + ' into ' + $('#index').val());
            $('#progress-bar').css('width', data.progress + '%');
        }

        if (data.action === "verify") {
            $('#panelheader').text('Verifying ' + $('#file').val().split('\\').pop());
        }
    }
};

$('#file').change(() => {
    uploadId = Math.random().toString(36).substring(7);
    $('#upload').hide();
    $('#uploadId').val(uploadId);
    $('#panelheader').text('Uploading ' + $('#file').val().split('\\').pop());
    $('#progress').show();
    websock.send(JSON.stringify({'uploadId': uploadId}));

    setTimeout(() => {
        $('#upload').submit();
    }, 500);
});

$(document).ready(() => {
    $('#progress').hide();

    $(() => {
        $("[data-toggle='tooltip']").tooltip();
    });
});

$('#close-window').click(() => {
    window.close();
});

$('#add-window').click(() => {
    window.open('http://' + location.host, '_blank');
});

$('#start-page').click(() => {
    window.location.href = '/';
});

$('#excel-options-show').click(() => {
    $('#excel-options').show();
    $('#excel-options-show').hide();
});