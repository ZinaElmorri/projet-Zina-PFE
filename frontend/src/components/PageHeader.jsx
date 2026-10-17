import { Flex, Space, Typography } from 'antd'

/** En-tête standard des pages : titre, sous-titre d'une ligne, actions à droite. */
export default function PageHeader({ title, subtitle, actions }) {
  return (
    <Flex justify="space-between" align="flex-start" gap={16} className="page-header">
      <div>
        <Typography.Title level={4} style={{ margin: 0 }}>{title}</Typography.Title>
        {subtitle && <Typography.Text type="secondary">{subtitle}</Typography.Text>}
      </div>
      {actions && <Space wrap>{actions}</Space>}
    </Flex>
  )
}
