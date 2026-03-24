import AdminDashboard from '../components/AdminDashboard'

export default function AdminPage() {
  return (
    <div
      className="min-h-screen bg-[#0A0A0F]"
      data-testid="admin-page"
    >
      <AdminDashboard />
    </div>
  )
}
