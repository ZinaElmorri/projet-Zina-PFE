export const COULEUR_PRINCIPALE = '#1F3A5F'
export const COULEUR_PRINCIPALE_FONCEE = '#18304F'
export const COULEUR_ACCENT = '#B8893B'

export const theme = {
  token: {
    colorPrimary: COULEUR_PRINCIPALE,
    colorInfo: COULEUR_PRINCIPALE,
    colorLink: '#2D5283',
    colorSuccess: '#2E7D32',
    colorWarning: '#D97706',
    colorError: '#C62828',
    colorBgLayout: '#F4F6F9',
    colorBorderSecondary: '#E5E9F0',
    borderRadius: 6,
    fontSize: 14,
    fontFamily: "'Inter', -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
    boxShadowTertiary: 'none',
  },
  components: {
    Layout: {
      siderBg: COULEUR_PRINCIPALE,
      triggerBg: COULEUR_PRINCIPALE_FONCEE,
      headerBg: '#FFFFFF',
      headerHeight: 56,
      headerPadding: '0 24px',
    },
    Menu: {
      darkItemBg: COULEUR_PRINCIPALE,
      darkSubMenuItemBg: COULEUR_PRINCIPALE,
      darkItemSelectedBg: '#2D5283',
      darkGroupTitleColor: 'rgba(255, 255, 255, 0.45)',
      itemBorderRadius: 4,
    },
    Table: { headerBg: '#F7F9FC', rowHoverBg: '#F0F4FA' },
    Card: { headerFontSize: 15 },
  },
}
