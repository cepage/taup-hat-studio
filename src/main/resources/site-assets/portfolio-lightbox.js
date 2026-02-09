/**
 * Portfolio Lightbox — vanilla JS full-screen image viewer.
 *
 * Expects a global `portfolioData` array (injected by Thymeleaf) with objects
 * containing `optimizedUrl` or `imageUrl`, `title`, and `description` fields.
 *
 * Features:
 *  - Click thumbnail to open full-screen lightbox
 *  - Previous / Next navigation buttons
 *  - Keyboard navigation (Arrow Left/Right, Escape)
 *  - Click overlay background to close
 *  - Image preloading
 */
(function () {
  'use strict';

  if (typeof portfolioData === 'undefined' || !portfolioData.length) return;

  var currentIndex = 0;
  var overlay = document.getElementById('lightbox');
  var lightboxImage = document.getElementById('lightboxImage');
  var lightboxCaption = document.getElementById('lightboxCaption');

  if (!overlay || !lightboxImage) return;

  function getFullUrl(item) {
    return item.optimizedUrl || item.imageUrl;
  }

  function updateLightbox() {
    var item = portfolioData[currentIndex];
    lightboxImage.src = getFullUrl(item);
    lightboxImage.alt = item.title || '';

    var caption = item.title || '';
    if (item.description) {
      caption += ' — ' + item.description;
    }
    lightboxCaption.textContent = caption;

    // Preload adjacent
    preload(currentIndex - 1);
    preload(currentIndex + 1);
  }

  function preload(index) {
    if (index >= 0 && index < portfolioData.length) {
      var img = new Image();
      img.src = getFullUrl(portfolioData[index]);
    }
  }

  // Global functions referenced by onclick attributes in the template
  window.openLightbox = function (index) {
    currentIndex = parseInt(index, 10);
    updateLightbox();
    overlay.classList.add('active');
    document.body.style.overflow = 'hidden';
  };

  window.closeLightbox = function () {
    overlay.classList.remove('active');
    document.body.style.overflow = '';
  };

  window.lightboxPrev = function () {
    if (currentIndex > 0) {
      currentIndex--;
      updateLightbox();
    }
  };

  window.lightboxNext = function () {
    if (currentIndex < portfolioData.length - 1) {
      currentIndex++;
      updateLightbox();
    }
  };

  // Close on overlay background click (not on the image itself)
  overlay.addEventListener('click', function (e) {
    if (e.target === overlay) {
      window.closeLightbox();
    }
  });

  // Keyboard navigation
  document.addEventListener('keydown', function (e) {
    if (!overlay.classList.contains('active')) return;

    switch (e.key) {
      case 'Escape':
        e.preventDefault();
        window.closeLightbox();
        break;
      case 'ArrowLeft':
        e.preventDefault();
        window.lightboxPrev();
        break;
      case 'ArrowRight':
        e.preventDefault();
        window.lightboxNext();
        break;
    }
  });
})();
