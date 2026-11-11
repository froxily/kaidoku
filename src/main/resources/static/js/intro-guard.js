(() => {
  try {
    if (location.pathname !== '/' && location.pathname !== '/index.html') return;
    if (sessionStorage.getItem('kaidoku:intro-played')) return;
    document.documentElement.classList.add('ki-intro-pending');
  } catch {
  }
})();
