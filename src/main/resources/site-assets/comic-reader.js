/**
 * Comic Reader — vanilla JS page navigation for the issue reader.
 *
 * Expects a global `comicPages` array (injected by Thymeleaf) with objects
 * containing at least `optimizedUrl` or `imageUrl` fields.
 *
 * Features:
 *  - Previous / Next page buttons
 *  - Keyboard navigation (Arrow Left/Right, Home/End)
 *  - Click on image to advance page
 *  - Page indicator (e.g. "3 / 12")
 *  - Image preloading for smooth transitions
 */
(function () {
  'use strict';

  if (typeof comicPages === 'undefined' || !comicPages.length) return;

  var currentPage = 0;
  var totalPages = comicPages.length;

  var readerImage = document.getElementById('readerImage');
  var prevBtn = document.getElementById('prevPage');
  var nextBtn = document.getElementById('nextPage');
  var indicator = document.getElementById('pageIndicator');
  var viewport = document.getElementById('readerViewport');

  if (!readerImage || !prevBtn || !nextBtn) return;

  function getPageUrl(page) {
    return page.optimizedUrl || page.imageUrl;
  }

  function updatePage() {
    readerImage.src = getPageUrl(comicPages[currentPage]);
    readerImage.alt = 'Page ' + (currentPage + 1);
    indicator.textContent = (currentPage + 1) + ' / ' + totalPages;
    prevBtn.disabled = currentPage === 0;
    nextBtn.disabled = currentPage === totalPages - 1;

    // Preload adjacent pages
    preload(currentPage + 1);
    preload(currentPage + 2);
  }

  function preload(index) {
    if (index >= 0 && index < totalPages) {
      var img = new Image();
      img.src = getPageUrl(comicPages[index]);
    }
  }

  function goToPage(index) {
    if (index >= 0 && index < totalPages) {
      currentPage = index;
      updatePage();
      // Scroll to top of reader on page change
      viewport.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  prevBtn.addEventListener('click', function () {
    goToPage(currentPage - 1);
  });

  nextBtn.addEventListener('click', function () {
    goToPage(currentPage + 1);
  });

  // Click on the image to advance
  viewport.addEventListener('click', function (e) {
    if (e.target === readerImage || e.target === viewport) {
      // Click on left third goes back, right two-thirds goes forward
      var rect = viewport.getBoundingClientRect();
      var clickX = e.clientX - rect.left;
      if (clickX < rect.width / 3) {
        goToPage(currentPage - 1);
      } else {
        goToPage(currentPage + 1);
      }
    }
  });

  // Keyboard navigation
  document.addEventListener('keydown', function (e) {
    // Don't capture if focus is on an input or textarea
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

    switch (e.key) {
      case 'ArrowLeft':
      case 'a':
        e.preventDefault();
        goToPage(currentPage - 1);
        break;
      case 'ArrowRight':
      case 'd':
        e.preventDefault();
        goToPage(currentPage + 1);
        break;
      case 'Home':
        e.preventDefault();
        goToPage(0);
        break;
      case 'End':
        e.preventDefault();
        goToPage(totalPages - 1);
        break;
    }
  });

  // Preload first few pages
  preload(1);
  preload(2);
})();
