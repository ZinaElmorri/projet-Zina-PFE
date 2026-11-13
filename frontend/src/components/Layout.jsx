import { Suspense, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Breadcrumb, Dropdown, Layout as AntLayout, Menu, Skeleton, Space, Tag, Typography } from 'antd'
import {
  AlertOutlined,
  AuditOutlined,
  BankOutlined,
  BarChartOutlined,
  CheckSquareOutlined,
  DashboardOutlined,
  DownOutlined,
  FileSearchOutlined,
  HistoryOutlined,
  LogoutOutlined,
  SendOutlined,
  SwapOutlined,
  TeamOutlined,
  UserOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons'
import { useAuth } from '../context/AuthContext.jsx'
import {
  ROLES,
  peutAffecter,
  peutConsulterAudit,
  peutGererSocietes,
  peutGererUtilisateurs,
  peutPreparer,
  peutValider,
} from '../constants/roles.js'

const { Sider, Header, Content } = AntLayout

// Logo de la barre latérale ; si une image ne se charge pas, le nom en texte la remplace.
function LogoBarre({ replie }) {
  const [srcEnEchec, setSrcEnEchec] = useState(null)
  const src = replie ? '/icone_sepapaie_512.png' : '/logo_sepapaie_blanc.png'
  return (
    <Link to="/" className="app-brand" aria-label="SEPAPaie — tableau de bord">
      {srcEnEchec === src ? (
        <span className="app-brand__fallback">SEPAPaie</span>
      ) : (
        <img
          src={src}
          alt="SEPAPaie"
          className={replie ? 'app-brand__icon' : 'app-brand__logo'}
          onError={() => setSrcEnEchec(src)}
        />
      )}
    </Link>
  )
}

const HISTORIQUE = '/virements-masse/historique'
const RESULTAT = '/virements-masse/resultat'
const A_VALIDER = '/virements-masse/a-valider'

const FIL_ARIANE = {
  '/': ['Pilotage', 'Tableau de bord'],
  '/kpi': ['Pilotage', 'Indicateurs KPI'],
  '/transactions': ['Paie et virements', 'Transactions'],
  '/virements-masse': ['Paie et virements', 'Virements de masse', 'Nouveau virement'],
  [HISTORIQUE]: ['Paie et virements', 'Virements de masse', 'Historique des fichiers'],
  [A_VALIDER]: ['Paie et virements', 'Virements de masse', 'Fichiers à valider'],
  [RESULTAT]: ['Paie et virements', 'Virements de masse', 'Résultat du contrôle'],
  '/affectations': ['Paie et virements', 'Affectation des sociétés'],
  '/societes': ['Administration', 'Sociétés débitrices'],
  '/anomalies': ['Contrôle', 'Anomalies'],
  '/xsd': ['Contrôle', 'Schémas XSD'],
  '/users': ['Administration', 'Utilisateurs'],
  '/audit': ['Administration', "Journal d'audit"],
  '/profil': ['Mon compte', 'Profil'],
}

// Pages déjà migrées vers antd : elles gèrent elles-mêmes leurs blocs blancs.
const PAGES_MIGREES = new Set([])

// Visibilité des entrées : règles centralisées dans constants/roles.js.
function menuItems(role) {
  const groupes = [
    {
      type: 'group', label: 'Pilotage', children: [
        { key: '/', icon: <DashboardOutlined />, label: 'Tableau de bord' },
        { key: '/kpi', icon: <BarChartOutlined />, label: 'Indicateurs KPI' },
      ],
    },
    {
      type: 'group', label: 'Paie et virements', children: [
        { key: '/transactions', icon: <SwapOutlined />, label: 'Transactions' },
        ...(peutPreparer(role) ? [{ key: '/virements-masse', icon: <SendOutlined />, label: 'Virements de masse' }] : []),
        ...(peutValider(role) ? [{ key: A_VALIDER, icon: <CheckSquareOutlined />, label: 'Fichiers à valider' }] : []),
        { key: HISTORIQUE, icon: <HistoryOutlined />, label: 'Historique SEPA' },
        ...(peutAffecter(role) ? [{ key: '/affectations', icon: <UsergroupAddOutlined />, label: 'Affectation des sociétés' }] : []),
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
        ...(peutGererUtilisateurs(role) ? [{ key: '/users', icon: <TeamOutlined />, label: 'Utilisateurs' }] : []),
        ...(peutGererSocietes(role) ? [{ key: '/societes', icon: <BankOutlined />, label: 'Sociétés débitrices' }] : []),
        ...(peutConsulterAudit(role) ? [{ key: '/audit', icon: <AuditOutlined />, label: "Journal d'audit" }] : []),
      ],
    },
  ]
  return groupes.filter((groupe) => groupe.children.length > 0)
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
        <LogoBarre replie={collapsed} />
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
