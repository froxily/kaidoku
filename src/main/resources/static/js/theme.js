(() => {
  try {
    const mode = localStorage.getItem('kaidoku.theme');
    if (mode === 'light' || mode === 'dark') {
      document.documentElement.dataset.theme = mode;
    }
  } catch {
  }
})();
