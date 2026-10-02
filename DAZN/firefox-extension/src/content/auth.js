(function (scope) {
  'use strict';
  const NS = scope.NHLUK = scope.NHLUK || {};
  function isAuthURL(value) {
    try {
      const url = new URL(value);
      return url.protocol === 'https:' && url.hostname === 'www.dazn.com' &&
        !url.port && !url.username && !url.password &&
        /^\/[a-z]{2}-[a-z]{2}\/signin\/?$/i.test(url.pathname);
    } catch (_) { return false; }
  }
  const signInUrl = 'https://www.dazn.com/en-GB/signin';
  function create({ shield, onChange = () => {}, onReturn = () => {} } = {}) {
    const doc = scope.document;
    let form = null;
    let active = false;
    let pendingReturn = false;
    let scheduled = false;
    let previousRoute = scope.location.href;
    function close() {
      const wasActive = active;
      active = false;
      form = null;
      doc.documentElement.removeAttribute('data-nhluk-auth');
      for (const node of doc.querySelectorAll('[data-nhluk-auth-allowed],[data-nhluk-auth-parent],[data-nhluk-auth-form]')) {
        node.removeAttribute('data-nhluk-auth-allowed');
        node.removeAttribute('data-nhluk-auth-parent');
        node.removeAttribute('data-nhluk-auth-form');
      }
      if (wasActive) onChange({ state: 'covered' });
    }
    function refresh() {
      scheduled = false;
      if (!isAuthURL(scope.location.href)) {
        close();
        if (pendingReturn) { pendingReturn = false; onReturn(); }
        return;
      }
      // Observe structure and attributes only. Password/input values are never read.
      const forms = [...doc.querySelectorAll('form')].filter(candidate =>
        candidate.querySelector('input[type="password"],input[type="email"],input[autocomplete="username"]'));
      if (forms.length !== 1) { close(); return; }
      const candidate = forms[0];
      if (form !== candidate) { close(); shield?.cover('auth'); }
      form = candidate;
      for (let parent = form.parentElement; parent && parent !== doc.documentElement; parent = parent.parentElement) {
        parent.setAttribute('data-nhluk-auth-parent', '');
      }
      form.setAttribute('data-nhluk-auth-form', '');
      const controls = [...form.querySelectorAll('input,button,select')].filter(node => {
        if (node.tagName !== 'INPUT') return true;
        return ['email', 'password', 'text', 'submit', 'button', 'checkbox'].includes(node.type);
      });
      const allowed = new Set(controls);
      for (const control of controls) {
        // Visible textual button descendants and matching form labels are needed
        // for normal provider login; links, images and arbitrary panels stay hidden.
        if (control.tagName === 'BUTTON') {
          for (const child of control.querySelectorAll('span,strong,b')) allowed.add(child);
        }
        for (const label of form.querySelectorAll('label')) {
          if (label.control === control) {
            allowed.add(label);
            for (const child of label.querySelectorAll('span,strong,b')) allowed.add(child);
          }
        }
      }
      for (const node of doc.querySelectorAll('[data-nhluk-auth-allowed]')) {
        if (!allowed.has(node)) node.removeAttribute('data-nhluk-auth-allowed');
      }
      for (const node of allowed) {
        node.setAttribute('data-nhluk-auth-allowed', '');
        for (let parent = node.parentElement; parent && parent !== form; parent = parent.parentElement) {
          parent.setAttribute('data-nhluk-auth-parent', '');
        }
      }
      doc.documentElement.setAttribute('data-nhluk-auth', 'open');
      pendingReturn = true;
      if (!active) { active = true; onChange({ state: 'signing-in' }); }
    }
    function queueRefresh() {
      if (!scheduled) { scheduled = true; scope.queueMicrotask(refresh); }
    }
    function navigate() { close(); shield?.cover('navigation'); queueRefresh(); }
    const observer = new scope.MutationObserver(queueRefresh);
    observer.observe(doc, { childList: true, subtree: true });
    scope.addEventListener('nhluk:navigation', navigate);
    scope.addEventListener('popstate', navigate);
    scope.addEventListener('pagehide', navigate);
    // Backup for a page that replaces the MAIN history wrappers. This is not the
    // primary navigation barrier; page-guard closes synchronously before history.
    const timer = scope.setInterval(() => {
      if (previousRoute !== scope.location.href) { previousRoute = scope.location.href; navigate(); }
    }, 100);
    refresh();
    return {
      refresh, close, isActive: () => active,
      signIn() { close(); shield?.cover('auth'); scope.location.assign(signInUrl); },
      destroy() {
        close(); observer.disconnect(); scope.clearInterval(timer);
        scope.removeEventListener('nhluk:navigation', navigate);
        scope.removeEventListener('popstate', navigate);
        scope.removeEventListener('pagehide', navigate);
      }
    };
  }
  NS.auth = { create, isAuthURL, isAuthUrl: isAuthURL, signInUrl };
  if (typeof module !== 'undefined' && module.exports) module.exports = NS.auth;
})(globalThis);
