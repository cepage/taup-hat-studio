/**
 * Set Viewer — vertical scroll viewer with thumbnail sidebar navigation.
 *
 * Uses IntersectionObserver to highlight the sidebar thumbnail corresponding
 * to the currently visible item in the main content area. Clicking a thumbnail
 * smooth-scrolls to the matching item. Arrow keys navigate between items.
 */
(function () {
  'use strict';

  var sidebar = document.getElementById('setViewerSidebar');
  var content = document.getElementById('setViewerContent');
  if (!sidebar || !content) return;

  var items = content.querySelectorAll('.set-viewer-item');
  var thumbs = sidebar.querySelectorAll('.sidebar-thumb');
  if (!items.length) return;

  var activeIndex = 0;

  function setActive(index) {
    if (index < 0 || index >= items.length || index === activeIndex) return;
    thumbs[activeIndex].classList.remove('active');
    thumbs[index].classList.add('active');
    activeIndex = index;

    // Scroll the active thumbnail into view within the sidebar
    thumbs[index].scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  // Scroll-spy via IntersectionObserver
  var observer = new IntersectionObserver(
    function (entries) {
      var bestEntry = null;
      var bestRatio = 0;

      for (var i = 0; i < entries.length; i++) {
        if (entries[i].isIntersecting && entries[i].intersectionRatio > bestRatio) {
          bestRatio = entries[i].intersectionRatio;
          bestEntry = entries[i];
        }
      }

      if (bestEntry) {
        var index = parseInt(bestEntry.target.getAttribute('data-index'), 10);
        if (!isNaN(index)) {
          setActive(index);
        }
      }
    },
    {
      root: null,
      rootMargin: '-10% 0px -60% 0px',
      threshold: [0, 0.25, 0.5, 0.75, 1],
    }
  );

  for (var i = 0; i < items.length; i++) {
    observer.observe(items[i]);
  }

  // Thumbnail click → scroll to item
  for (var j = 0; j < thumbs.length; j++) {
    thumbs[j].addEventListener('click', (function (index) {
      return function () {
        items[index].scrollIntoView({ behavior: 'smooth', block: 'start' });
      };
    })(j));
  }

  // Keyboard navigation
  document.addEventListener('keydown', function (e) {
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

    switch (e.key) {
      case 'ArrowUp':
      case 'k':
        e.preventDefault();
        if (activeIndex > 0) {
          items[activeIndex - 1].scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
        break;
      case 'ArrowDown':
      case 'j':
        e.preventDefault();
        if (activeIndex < items.length - 1) {
          items[activeIndex + 1].scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
        break;
      case 'Home':
        e.preventDefault();
        items[0].scrollIntoView({ behavior: 'smooth', block: 'start' });
        break;
      case 'End':
        e.preventDefault();
        items[items.length - 1].scrollIntoView({ behavior: 'smooth', block: 'start' });
        break;
    }
  });
})();
