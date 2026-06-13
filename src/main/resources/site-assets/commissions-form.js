import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.14.0/firebase-app.js';
import { initializeAppCheck, ReCaptchaV3Provider } from 'https://www.gstatic.com/firebasejs/12.14.0/firebase-app-check.js';
import { getFirestore, collection, addDoc } from 'https://www.gstatic.com/firebasejs/12.14.0/firebase-firestore.js';

(function () {
  var form = document.getElementById('commissionsForm');
  if (!form) return;

  var email      = form.getAttribute('data-email') || '';
  var success    = document.getElementById('commissionsSuccess');
  var errorBox   = document.getElementById('commissionsError');
  var copyBtn    = document.getElementById('commissionsCopyEmail');

  var db = null;
  try {
    var app = initializeApp(window.__FIREBASE_CONFIG__);
    initializeAppCheck(app, {
      provider: new ReCaptchaV3Provider(window.__RECAPTCHA_SITE_KEY__),
      isTokenAutoRefreshEnabled: true
    });
    db = getFirestore(app);
  } catch (err) {
    db = null;
  }

  if (copyBtn) {
    copyBtn.addEventListener('click', function () {
      var address = copyBtn.getAttribute('data-email') || email;
      navigator.clipboard.writeText(address).then(function () {
        var original = copyBtn.textContent;
        copyBtn.textContent = 'Copied!';
        setTimeout(function () { copyBtn.textContent = original; }, 2000);
      });
    });
  }

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
    var text = [
      'Name: '   + get('name'),
      'Email: '  + get('email'),
      'Phone: '  + get('phone'),
      'Type: '   + get('commission-type'),
      'Budget: ' + get('budget'),
      '',
      'Description:',
      get('description')
    ].join('\n');

    var submitBtn = form.querySelector('button[type="submit"]');
    if (submitBtn) submitBtn.disabled = true;

    var fail = function () {
      if (submitBtn) submitBtn.disabled = false;
      if (errorBox) errorBox.hidden = false;
    };

    if (!db) {
      fail();
      return;
    }

    addDoc(collection(db, 'mail'), {
      to: email,
      replyTo: get('email'),
      message: { subject: subject, text: text }
    }).then(function () {
      form.hidden = true;
      if (success) success.hidden = false;
    }).catch(fail);
  });
})();
