/**
 * AGENDA+ LANDING PAGE INTERACTIVE SCRIPT
 * Theme Switcher, Interactive Phone Tabs, and Widget Size toggles
 */

document.addEventListener('DOMContentLoaded', () => {

  // --- Theme Switcher (Nothing OS vs Material You) ---
  const themeSwitchBtn = document.getElementById('theme-switch-btn');
  const themeBtnLabel = document.getElementById('theme-btn-label');
  let isMaterialYou = false;

  themeSwitchBtn.addEventListener('click', () => {
    isMaterialYou = !isMaterialYou;
    document.body.classList.toggle('material-you-theme', isMaterialYou);
    themeBtnLabel.textContent = isMaterialYou ? 'Material You' : 'Nothing OS';
  });

  // --- Interactive Phone Mockup Tabs ---
  const tabButtons = document.querySelectorAll('.phone-tab-btn');
  const panes = document.querySelectorAll('.pane-content');

  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetTab = btn.getAttribute('data-tab');

      tabButtons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      panes.forEach(pane => {
        pane.classList.remove('active');
        if (pane.id === `pane-${targetTab}`) {
          pane.classList.add('active');
        }
      });
    });
  });

  // --- Interactive Home Screen Widgets Toggle ---
  const widgetCtrlBtns = document.querySelectorAll('.widget-ctrl-btn');
  const widgetBoxes = document.querySelectorAll('.widgets-grid .widget-box');

  widgetCtrlBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const size = btn.getAttribute('data-widget-size');

      widgetCtrlBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      widgetBoxes.forEach(box => {
        if (size === 'compact') {
          box.style.gridColumn = 'span 1';
        } else {
          box.style.gridColumn = '';
        }
      });
    });
  });

  // --- Privacy Modal ---
  const privacyLink = document.getElementById('privacy-link');
  const privacyModal = document.getElementById('privacy-modal');
  const closeModalBtn = document.getElementById('close-modal-btn');

  privacyLink.addEventListener('click', (e) => {
    e.preventDefault();
    privacyModal.classList.add('active');
  });

  closeModalBtn.addEventListener('click', () => {
    privacyModal.classList.remove('active');
  });

  privacyModal.addEventListener('click', (e) => {
    if (e.target === privacyModal) {
      privacyModal.classList.remove('active');
    }
  });

});
