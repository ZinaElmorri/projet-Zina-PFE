import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App as AntApp, ConfigProvider } from 'antd'
import frFR from 'antd/locale/fr_FR'
import dayjs from 'dayjs'
import 'dayjs/locale/fr'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import App from './App.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { theme } from './theme/theme.js'
import './styles.css'

dayjs.locale('fr')

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider locale={frFR} theme={theme}>
      <AntApp>
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>
)
