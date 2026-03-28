/**
 * Decorative Star Background — scatters star images that twinkle
 * and drift across the page, fading based on scroll proximity.
 */
(function () {
  'use strict';

  var container = document.getElementById('starsContainer');
  if (!container) return;

  var STAR_COUNT = 22;
  var STAR_IMG =
    '<img src="/images/star.png" alt="" aria-hidden="true" draggable="false" ' +
    'style="width:100%;height:100%">';

  var stars = [];
  var docH = 1;

  for (var i = 0; i < STAR_COUNT; i++) {
    var el = document.createElement('div');
    el.className = 'star-decoration';
    el.innerHTML = STAR_IMG;

    var size = 14 + Math.random() * 30;
    var left = Math.random() * 96 + 2;
    var top = Math.random() * 100;
    var baseOpacity = 0.15 + Math.random() * 0.35;
    var delay = Math.random() * 5;
    var duration = 3 + Math.random() * 4;

    el.style.cssText =
      'width:' + size + 'px;height:' + size + 'px;' +
      'left:' + left + '%;top:' + top + '%;' +
      'animation-delay:' + delay + 's;' +
      'animation-duration:' + duration + 's;';

    container.appendChild(el);
    stars.push({ el: el, baseOpacity: baseOpacity, topPct: top });
  }

  function updateDocHeight() {
    docH = Math.max(1, document.documentElement.scrollHeight);
  }

  function onScroll() {
    var scrollY = window.scrollY || window.pageYOffset;
    var viewH = window.innerHeight;
    var viewTop = scrollY / docH;
    var viewBottom = (scrollY + viewH) / docH;
    var viewCenter = (viewTop + viewBottom) / 2;
    var viewSpan = viewBottom - viewTop;

    for (var i = 0; i < stars.length; i++) {
      var s = stars[i];
      var starPos = s.topPct / 100;
      var dist = Math.abs(starPos - viewCenter);
      var fade = Math.max(0, 1 - (dist / (viewSpan * 1.2)));
      s.el.style.opacity = (s.baseOpacity * fade).toFixed(3);
    }
  }

  var ticking = false;
  window.addEventListener('scroll', function () {
    if (!ticking) {
      requestAnimationFrame(function () {
        onScroll();
        ticking = false;
      });
      ticking = true;
    }
  }, { passive: true });

  window.addEventListener('resize', function () {
    updateDocHeight();
    onScroll();
  }, { passive: true });

  updateDocHeight();
  onScroll();
})();
