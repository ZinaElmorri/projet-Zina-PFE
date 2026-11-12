import { Suspense, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Breadcrumb, Dropdown, Layout as AntLayout, Menu, Skeleton, Space, Tag, Typography } from 'antd'
import {
  AlertOutlined,
  AuditOutlined,
  BankOutlined,
  BarChartOutlined,
  DashboardOutlined,
  DownOutlined,
  FileSearchOutlined,
  HistoryOutlined,
  LogoutOutlined,
  SendOutlined,
  SwapOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useAuth } from '../context/AuthContext.jsx'

const { Sider, Header, Content } = AntLayout

export const ROLES = {
  ADMINISTRATEUR: { label: 'Administrateur', color: 'geekblue' },
  GESTIONNAIRE_RH: { label: 'Gestionnaire RH', color: 'cyan' },
  VALIDATEUR: { label: 'Validateur', color: 'green' },
  AUDITEUR: { label: 'Auditeur', color: 'purple' },
}

const HISTORIQUE = '/virements-masse/historique'
const RESULTAT = '/virements-masse/resultat'

const FIL_ARIANE = {
  '/': ['Pilotage', 'Tableau de bord'],
  '/kpi': ['Pilotage', 'Indicateurs KPI'],
  '/transactions': ['Paie et virements', 'Transactions'],
  '/virements-masse': ['Paie et virements', 'Virements de masse', 'Nouveau virement'],
  [HISTORIQUE]: ['Paie et virements', 'Virements de masse', 'Historique des fichiers'],
  [RESULTAT]: ['Paie et virements', 'Virements de masse', 'Résultat du contrôle'],
  '/societes': ['Paie et virements', 'Sociétés débitrices'],
  '/anomalies': ['Contrôle', 'Anomalies'],
  '/xsd': ['Contrôle', 'Schémas XSD'],
  '/users': ['Administration', 'Utilisateurs'],
  '/audit': ['Administration', "Journal d'audit"],
  '/profil': ['Mon compte', 'Profil'],
}

// Pages déjà migrées vers antd : elles gèrent elles-mêmes leurs blocs blancs.
const PAGES_MIGREES = new Set([])

function menuItems(role) {
  const estAdmin = role === 'ADMINISTRATEUR'
  const peutGererSocietes = estAdmin || role === 'GESTIONNAIRE_RH'
  return [
    {
      type: 'group', label: 'Pilotage', children: [
        { key: '/', icon: <DashboardOutlined />, label: 'Tableau de bord' },
        { key: '/kpi', icon: <BarChartOutlined />, label: 'Indicateurs KPI' },
      ],
    },
    {
      type: 'group', label: 'Paie et virements', children: [
        { key: '/transactions', icon: <SwapOutlined />, label: 'Transactions' },
        { key: '/virements-masse', icon: <SendOutlined />, label: 'Virements de masse' },
        { key: HISTORIQUE, icon: <HistoryOutlined />, label: 'Historique SEPA' },
        ...(peutGererSocietes ? [{ key: '/societes', icon: <BankOutlined />, label: 'Sociétés débitrices' }] : []),
      ],
    },
    {
      type: 'group', label: 'Contrôle', children: [
        { key: '/anomalies', icon: <AlertOutlined />, label: 'Anomalies' },
        { key: '/xsd', icon: <FileSearchOutlined />, label: 'Schémas XSD' },
      ],
    },
    {
      type: 'group', label: 'Administration', children: [
        ...(estAdmin ? [{ key: '/users', icon: <TeamOutlined />, label: 'Utilisateurs' }] : []),
        { key: '/audit', icon: <AuditOutlined />, label: "Journal d'audit" },
      ],
    },
  ]
}

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [collapsed, setCollapsed] = useState(false)

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  // Les pages de résultat restent rattachées à « Virements de masse » dans le menu.
  const surResultat = pathname.startsWith(RESULTAT)
  const cleActive = surResultat ? '/virements-masse' : pathname
  const [section, titre, sousTitre] = FIL_ARIANE[surResultat ? RESULTAT : pathname] || []
  const role = ROLES[user?.role] || { label: user?.role, color: 'default' }

  const menuUtilisateur = {
    items: [
      { key: 'profil', icon: <UserOutlined />, label: 'Profil' },
      { type: 'divider' },
      { key: 'logout', icon: <LogoutOutlined />, label: 'Déconnexion', danger: true },
    ],
    onClick: ({ key }) => (key === 'logout' ? handleLogout() : navigate('/profil')),
  }

  const page = (
    <Suspense fallback={<Skeleton active paragraph={{ rows: 8 }} />}>
      <Outlet />
    </Suspense>
  )

  return (
    <AntLayout className="app-shell">
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        width={240}
        className="app-sider"
      >
        <div className="app-brand">
          <span className="app-brand__mark">SP</span>
          {!collapsed && (
            <span className="app-brand__text">
              <span className="app-brand__name">SEPA Paie</span>
              <span className="app-brand__sub">Back-office virements</span>
            </span>
          )}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          items={menuItems(user?.role)}
          selectedKeys={[cleActive]}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <AntLayout>
        <Header className="app-header">
          <Breadcrumb items={[section, titre, sousTitre].filter(Boolean).map((t) => ({ title: t }))} />
          <Dropdown menu={menuUtilisateur} trigger={['click']} placement="bottomRight">
            <Space className="app-user" size={8}>
              <UserOutlined />
              <Typography.Text strong>{user?.username}</Typography.Text>
              <Tag color={role.color} bordered={false} style={{ marginInlineEnd: 0 }}>{role.label}</Tag>
              <DownOutlined style={{ fontSize: 10 }} />
            </Space>
          </Dropdown>
        </Header>
        <Content className="app-content">
          {PAGES_MIGREES.has(pathname) ? page : <div className="content-panel">{page}</div>}
        </Content>
      </AntLayout>
    </AntLayout>
  )
}
