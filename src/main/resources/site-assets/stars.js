/**
 * Decorative Star Background — generates 4-pointed star SVGs that
 * fade in/out as the user scrolls, creating a subtle parallax effect.
 */
(function () {
  'use strict';

  var container = document.getElementById('starsContainer');
  if (!container) return;

  var STAR_COUNT = 18;
  var STAR_SVG =
    '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">' +
    '<path d="M12 0 L14.5 9.5 L24 12 L14.5 14.5 L12 24 L9.5 14.5 L0 12 L9.5 9.5 Z" fill="currentColor"/>' +
    '</svg>';

  var stars = [];

  for (var i = 0; i < STAR_COUNT; i++) {
    var el = document.createElement('div');
    el.className = 'star-decoration';
    el.innerHTML = STAR_SVG;

    var size = 12 + Math.random() * 28;
    var left = Math.random() * 100;
    var top = Math.random() * 100;
    var baseOpacity = 0.08 + Math.random() * 0.18;
    var delay = Math.random() * 6;
    var duration = 3 + Math.random() * 4;

    el.style.cssText =
      'width:' + size + 'px;height:' + size + 'px;' +
      'left:' + left + '%;top:' + top + '%;' +
      'opacity:0;' +
      'animation-delay:' + delay + 's;' +
      'animation-duration:' + duration + 's;';

    container.appendChild(el);
    stars.push({ el: el, baseOpacity: baseOpacity, top: top });
  }

  function onScroll() {
    var scrollY = window.scrollY || window.pageYOffset;
    var viewH = window.innerHeight;
    var docH = document.documentElement.scrollHeight;
    var scrollRatio = scrollY / (docH - viewH || 1);

    for (var i = 0; i < stars.length; i++) {
      var s = stars[i];
      var dist = Math.abs(s.top / 100 - scrollRatio);
      var fade = Math.max(0, 1 - dist * 2.5);
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

  onScroll();
})();
