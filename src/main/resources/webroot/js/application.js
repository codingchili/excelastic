let websock = new WebSocket("ws://" + location.host + "/");
let uploadId = '';

websock.onmessage = (e) => {
    let data = JSON.parse(e.data);
    console.log(e.data);

    if (data.uploadId === uploadId) {
        $('#panelheader').text('Importing ' + $('#file').val().split('\\').pop() + ' into ' + $('#index').val());
        $('#progress-bar').css('width', data.progress + '%');
        $
    }
}

$('#file').change(function () {
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

$(document).ready(function () {
    var date = new Date();
    $('#progress').hide();
    $('#index').val(date.toLocaleString('en-us', {month: 'long'}).toLowerCase() + '-' + date.getFullYear());
    $(function () {
        $("[data-toggle='tooltip']").tooltip();
    });
});

$('#close-window').click(function () {
    window.close();
});

$('#add-window').click(function () {
    window.open('http://' + location.host, '_blank');
});

$('#start-page').click(function () {
    window.location.href = '/';
});