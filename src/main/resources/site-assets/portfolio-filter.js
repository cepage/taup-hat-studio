(function () {
  var grid = document.getElementById('portfolio-gallery');
  var bar  = document.getElementById('portfolioFilters');
  if (!grid || !bar) return;

  var items = Array.prototype.slice.call(grid.querySelectorAll('.masonry-item'));

  // Unique, non-empty categories (preserve first-seen order)
  var cats = [];
  items.forEach(function (el) {
    var c = (el.getAttribute('data-category') || '').trim();
    if (c && cats.indexOf(c) === -1) cats.push(c);
  });

  if (cats.length < 2) return; // nothing worth filtering

  function makeChip(label, value) {
    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'filter-chip';
    b.textContent = label;
    b.setAttribute('data-filter', value);
    return b;
  }

  bar.appendChild(makeChip('All', '*'));
  cats.forEach(function (c) { bar.appendChild(makeChip(c, c)); });
  bar.firstChild.classList.add('active');
  bar.hidden = false;

  bar.addEventListener('click', function (e) {
    var chip = e.target.closest('.filter-chip');
    if (!chip) return;
    var filter = chip.getAttribute('data-filter');

    bar.querySelectorAll('.filter-chip').forEach(function (c) {
      c.classList.toggle('active', c === chip);
    });

    items.forEach(function (el) {
      var c = (el.getAttribute('data-category') || '').trim();
      el.hidden = (filter !== '*' && c !== filter);
    });
  });
})();
