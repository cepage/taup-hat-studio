(function () {
  var form = document.getElementById('commissionsForm');
  if (!form) return;

  var email   = form.getAttribute('data-email') || '';
  var success = document.getElementById('commissionsSuccess');

  form.addEventListener('submit', function (e) {
    e.preventDefault();

    // Honeypot — silently ignore bot submissions
    var hp = form.querySelector('#company');
    if (hp && hp.value) return;

    if (!form.reportValidity()) return;

    var get = function (id) {
      var el = form.querySelector('#' + id);
      return el ? el.value.trim() : '';
    };

    var subject = 'Commission request — ' + (get('name') || 'New');
    var body = [
      'Name: '   + get('name'),
      'Email: '  + get('email'),
      'Phone: '  + get('phone'),
      'Type: '   + get('commission-type'),
      'Budget: ' + get('budget'),
      '',
      'Description:',
      get('description')
    ].join('\n');

    window.location.href = 'mailto:' + encodeURIComponent(email)
      + '?subject=' + encodeURIComponent(subject)
      + '&body='    + encodeURIComponent(body);

    if (success) {
      form.hidden = true;
      success.hidden = false;
    }
  });
})();
