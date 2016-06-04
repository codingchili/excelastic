$('#file').change(function () {
    $('#upload').submit();
});

$(document).ready(function () {
    var date = new Date();
    $('#index').val(date.toLocaleString('en-us', {month: 'long'}) + '-' + date.getFullYear());
});