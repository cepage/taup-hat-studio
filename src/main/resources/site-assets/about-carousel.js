(function () {
  var INTERVAL_MS = 5000;
  var images = document.querySelectorAll('.about-carousel-img');
  if (images.length < 2) return;

  var current = 0;

  function advance() {
    images[current].classList.remove('active');
    current = (current + 1) % images.length;
    images[current].classList.add('active');
  }

  setInterval(advance, INTERVAL_MS);
})();
