/**
 * Portfolio Lightbox — PhotoSwipe 5 integration.
 *
 * Initializes PhotoSwipe Lightbox on the portfolio gallery grid.
 * Each thumbnail <a> element uses data-pswp-width, data-pswp-height,
 * and data-pswp-caption attributes set by the Thymeleaf template.
 *
 * Features:
 *  - Pinch-to-zoom and pan on touch devices
 *  - Fullscreen viewing mode
 *  - Smooth open/close animations from thumbnail
 *  - Keyboard navigation (Arrow Left/Right, Escape)
 *  - Image captions with title and description
 *  - Preloading of adjacent images
 */
(function () {
  'use strict';

  var galleryEl = document.getElementById('portfolio-gallery');
  if (!galleryEl) return;

  var lightbox = new PhotoSwipeLightbox({
    gallery: '#portfolio-gallery',
    children: 'a',
    pswpModule: PhotoSwipe,

    // Animation settings
    showHideAnimationType: 'zoom',
    bgOpacity: 0.92,

    // Padding around the image
    padding: { top: 20, bottom: 20, left: 20, right: 20 },
  });

  // Add captions from data-pswp-caption attribute
  lightbox.on('uiRegister', function () {
    lightbox.pswp.ui.registerElement({
      name: 'custom-caption',
      order: 9,
      isButton: false,
      appendTo: 'root',
      onInit: function (el) {
        lightbox.pswp.on('change', function () {
          var slide = lightbox.pswp.currSlide;
          var caption = slide.data.element
            ? slide.data.element.getAttribute('data-pswp-caption')
            : '';
          el.innerHTML = caption || '';
        });
      },
    });
  });

  lightbox.init();
})();
