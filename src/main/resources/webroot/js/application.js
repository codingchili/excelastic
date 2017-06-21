$('#file').change(function () {
    $('#upload').hide();
    $('#panelheader').text('Uploading ' + $('#file').val().split('\\').pop() + ' into ' + $('#index').val());
    $('#progress').show();
    $('#upload').submit();
});

$(document).ready(function () {
    var date = new Date();
    $('#progress').hide();
    $('#index').val(date.toLocaleString('en-us', {month: 'long'}).toLowerCase() + '-' + date.getFullYear());
    $('#progress-bar').css('animation', '0.2s linear 0s normal none infinite progress-bar-stripes');
    $(function () {
        $("[data-toggle='tooltip']").tooltip();
    });
});

$('#close-window').click(function () {
    window.close();
});

$('#add-window').click(function () {
    window.open(window.location.href, '_blank');
});

$('#start-page').click(function () {
    window.location.href = '/';
});