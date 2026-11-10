'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  TenantMember,
  TenantInvitation,
  TenantMembershipRole,
  MEMBER_ROLE_LABEL,
  MEMBER_STATUS_COLOR,
  MEMBER_STATUS_LABEL,
} from '@/domain/entities/member.entity';
import {
  memberRepository,
  getCurrentTenantId,
} from '@/infrastructure/repositories/member.repository';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  Users,
  UserPlus,
  RefreshCw,
  Mail,
  Shield,
  Lock,
  Unlock,
  CheckCircle2,
  Copy,
  Clock,
  X,
} from 'lucide-react';
import { toast } from 'sonner';

export default function MembersPage() {
  const [activeTab, setActiveTab] = useState<'members' | 'invitations'>('members');
  const [members, setMembers] = useState<TenantMember[]>([]);
  const [invitations, setInvitations] = useState<TenantInvitation[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Invite modal state
  const [isInviteModalOpen, setIsInviteModalOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<TenantMembershipRole>('OPERATOR');

  // Success invite link modal
  const [createdInviteLink, setCreatedInviteLink] = useState<{ email: string; token: string } | null>(null);

  const tenantId = getCurrentTenantId();

  const getErrorMessage = (err: unknown, fallback: string): string => {
    return (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message || fallback;
  };

  const fetchData = useCallback(async () => {
    try {
      const [membersData, invitationsData] = await Promise.all([
        memberRepository.getMembers(tenantId),
        memberRepository.getInvitations(tenantId),
      ]);
      setMembers(membersData);
      setInvitations(invitationsData);
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, 'Không thể tải dữ liệu nhân sự.'));
    } finally {
      setIsLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    let active = true;
    Promise.all([
      memberRepository.getMembers(tenantId),
      memberRepository.getInvitations(tenantId),
    ])
      .then(([membersData, invitationsData]) => {
        if (active) {
          setMembers(membersData);
          setInvitations(invitationsData);
        }
      })
      .catch((err: unknown) => {
        toast.error(getErrorMessage(err, 'Không thể tải dữ liệu nhân sự.'));
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => { active = false; };
  }, [tenantId]);

  const handleInviteSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteEmail.trim()) {
      toast.error('Vui lòng nhập email');
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await memberRepository.inviteMember(
        { email: inviteEmail.trim(), role: inviteRole },
        tenantId
      );
      toast.success('Đã gửi lời mời thành công!');
      setIsInviteModalOpen(false);
      setInviteEmail('');
      setCreatedInviteLink({
        email: res.email,
        token: res.invite_token,
      });
      fetchData();
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, 'Không thể tạo lời mời.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleToggleStatus = async (member: TenantMember) => {
    const isSuspending = member.status === 'ACTIVE';
    const actionText = isSuspending ? 'khóa' : 'mở khóa';

    if (!confirm(`Bạn có chắc chắn muốn ${actionText} tài khoản của "${member.full_name || member.email}"?`)) {
      return;
    }

    try {
      const targetStatus = isSuspending ? 'SUSPENDED' : 'ACTIVE';
      await memberRepository.updateMemberStatus(member.membership_id, targetStatus, tenantId);
      toast.success(`Đã ${actionText} tài khoản thành công!`);
      fetchData();
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, `Không thể ${actionText} tài khoản.`));
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    toast.success('Đã sao chép vào bộ nhớ tạm!');
  };

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Users className="h-7 w-7 text-blue-600" />
            <h1 className="text-2xl font-bold tracking-tight text-gray-900">Quản lý Nhân sự</h1>
          </div>
          <p className="mt-1 text-sm text-gray-500">
            Xem danh sách nhân viên, mời nhân viên vận hành mới và quản trị trạng thái tài khoản.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="outline" size="sm" onClick={() => fetchData()} className="gap-2">
            <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
            Làm mới
          </Button>
          <Button onClick={() => setIsInviteModalOpen(true)} className="gap-2 bg-blue-600 hover:bg-blue-700">
            <UserPlus className="h-4 w-4" />
            Mời nhân viên
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-4 border-b border-gray-200">
        <button
          onClick={() => setActiveTab('members')}
          className={`pb-3 text-sm font-semibold transition-colors border-b-2 flex items-center gap-2 ${
            activeTab === 'members'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <Users className="h-4 w-4" />
          Thành viên ({members.length})
        </button>
        <button
          onClick={() => setActiveTab('invitations')}
          className={`pb-3 text-sm font-semibold transition-colors border-b-2 flex items-center gap-2 ${
            activeTab === 'invitations'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <Mail className="h-4 w-4" />
          Lời mời đang chờ ({invitations.filter((i) => i.status === 'PENDING').length})
        </button>
      </div>

      {/* Tab Content: Members */}
      {activeTab === 'members' && (
        <Card>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-gray-600">
                <thead className="border-b border-gray-200 bg-gray-50 text-xs font-semibold uppercase text-gray-700">
                  <tr>
                    <th className="px-6 py-4">Nhân sự</th>
                    <th className="px-6 py-4">Vai trò</th>
                    <th className="px-6 py-4">Trạng thái</th>
                    <th className="px-6 py-4">Ngày gia nhập</th>
                    <th className="px-6 py-4 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {isLoading ? (
                    <tr>
                      <td colSpan={5} className="py-12 text-center text-gray-400">
                        Đang tải danh sách nhân sự...
                      </td>
                    </tr>
                  ) : members.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="py-12 text-center text-gray-400">
                        Chưa có nhân viên nào trong doanh nghiệp.
                      </td>
                    </tr>
                  ) : (
                    members.map((member) => (
                      <tr key={member.membership_id} className="hover:bg-gray-50/70 transition-colors">
                        <td className="px-6 py-4">
                          <div>
                            <div className="font-semibold text-gray-900">{member.full_name || 'Chưa đặt tên'}</div>
                            <div className="text-xs text-gray-500 flex items-center gap-1 mt-0.5">
                              <Mail className="h-3 w-3 text-gray-400" />
                              {member.email}
                            </div>
                          </div>
                        </td>

                        <td className="px-6 py-4">
                          <span className="inline-flex items-center gap-1.5 rounded-md bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-800">
                            <Shield className="h-3 w-3 text-slate-500" />
                            {MEMBER_ROLE_LABEL[member.role] || member.role}
                          </span>
                        </td>

                        <td className="px-6 py-4">
                          <span
                            className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium border ${
                              MEMBER_STATUS_COLOR[member.status] || 'bg-gray-100 text-gray-700'
                            }`}
                          >
                            {MEMBER_STATUS_LABEL[member.status] || member.status}
                          </span>
                        </td>

                        <td className="px-6 py-4 text-xs text-gray-500">
                          {member.created_at ? new Date(member.created_at).toLocaleDateString('vi-VN') : '—'}
                        </td>

                        <td className="px-6 py-4 text-right">
                          {member.role !== 'OWNER' && (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleToggleStatus(member)}
                              className={`h-8 gap-1.5 text-xs ${
                                member.status === 'ACTIVE'
                                  ? 'text-rose-600 hover:text-rose-700 hover:bg-rose-50'
                                  : 'text-emerald-600 hover:text-emerald-700 hover:bg-emerald-50'
                              }`}
                            >
                              {member.status === 'ACTIVE' ? (
                                <>
                                  <Lock className="h-3.5 w-3.5" /> Khóa tài khoản
                                </>
                              ) : (
                                <>
                                  <Unlock className="h-3.5 w-3.5" /> Mở khóa
                                </>
                              )}
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Tab Content: Invitations */}
      {activeTab === 'invitations' && (
        <Card>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-gray-600">
                <thead className="border-b border-gray-200 bg-gray-50 text-xs font-semibold uppercase text-gray-700">
                  <tr>
                    <th className="px-6 py-4">Email được mời</th>
                    <th className="px-6 py-4">Vai trò dự kiến</th>
                    <th className="px-6 py-4">Trạng thái</th>
                    <th className="px-6 py-4">Hạn lời mời</th>
                    <th className="px-6 py-4">Thời gian tạo</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {isLoading ? (
                    <tr>
                      <td colSpan={5} className="py-12 text-center text-gray-400">
                        Đang tải danh sách lời mời...
                      </td>
                    </tr>
                  ) : invitations.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="py-12 text-center text-gray-400">
                        Không có lời mời nào.
                      </td>
                    </tr>
                  ) : (
                    invitations.map((inv) => (
                      <tr key={inv.id} className="hover:bg-gray-50/70 transition-colors">
                        <td className="px-6 py-4 font-medium text-gray-900">{inv.email}</td>

                        <td className="px-6 py-4">
                          <span className="inline-flex items-center gap-1.5 rounded-md bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-800">
                            {MEMBER_ROLE_LABEL[inv.role] || inv.role}
                          </span>
                        </td>

                        <td className="px-6 py-4">
                          <span
                            className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${
                              inv.status === 'PENDING'
                                ? 'bg-amber-100 text-amber-800'
                                : inv.status === 'ACCEPTED'
                                ? 'bg-emerald-100 text-emerald-800'
                                : 'bg-gray-100 text-gray-700'
                            }`}
                          >
                            {inv.status === 'PENDING'
                              ? 'Chờ chấp nhận'
                              : inv.status === 'ACCEPTED'
                              ? 'Đã chấp nhận'
                              : inv.status === 'EXPIRED'
                              ? 'Đã hết hạn'
                              : 'Đã thu hồi'}
                          </span>
                        </td>

                        <td className="px-6 py-4 text-xs text-gray-500">
                          <div className="flex items-center gap-1">
                            <Clock className="h-3 w-3 text-gray-400" />
                            {new Date(inv.expires_at).toLocaleString('vi-VN')}
                          </div>
                        </td>

                        <td className="px-6 py-4 text-xs text-gray-400">
                          {new Date(inv.created_at).toLocaleString('vi-VN')}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Invite Modal */}
      {isInviteModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between border-b border-gray-100 pb-3">
              <h2 className="text-lg font-bold text-gray-900">Mời nhân viên vào doanh nghiệp</h2>
              <button onClick={() => setIsInviteModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleInviteSubmit} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-medium text-gray-700">Email nhân viên *</label>
                <input
                  type="email"
                  required
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  placeholder="nhanvien@doanhnghiep.vn"
                  className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-700">Vai trò trong doanh nghiệp *</label>
                <select
                  value={inviteRole}
                  onChange={(e) => setInviteRole(e.target.value as TenantMembershipRole)}
                  className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none bg-white"
                >
                  <option value="OPERATOR">Nhân viên vận hành (OPERATOR)</option>
                  <option value="ADMIN">Quản trị viên chi nhánh (ADMIN)</option>
                  <option value="DRIVER">Tài xế nội bộ (DRIVER)</option>
                  <option value="VIEWER">Người xem báo cáo (VIEWER)</option>
                </select>
                <p className="mt-1 text-xs text-gray-500">
                  Nhân viên vận hành có quyền tạo đơn, quản lý điểm lấy hàng và theo dõi điều phối.
                </p>
              </div>

              <div className="flex justify-end gap-3 border-t border-gray-100 pt-4">
                <Button type="button" variant="outline" onClick={() => setIsInviteModalOpen(false)}>
                  Hủy
                </Button>
                <Button type="submit" disabled={isSubmitting} className="bg-blue-600 hover:bg-blue-700">
                  {isSubmitting ? 'Đang tạo...' : 'Tạo lời mời'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Success Invite Token Display Modal */}
      {createdInviteLink && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
            <div className="flex items-center gap-3 text-emerald-600">
              <CheckCircle2 className="h-6 w-6" />
              <h2 className="text-lg font-bold text-gray-900">Tạo lời mời thành công!</h2>
            </div>
            <p className="mt-2 text-sm text-gray-600">
              Đã tạo mã kích hoạt cho nhân viên <strong>{createdInviteLink.email}</strong>. Bạn có thể gửi mã hoặc đường dẫn sau để nhân viên kích hoạt tài khoản:
            </p>

            <div className="mt-4 rounded-lg bg-gray-50 p-3 border border-gray-200">
              <label className="block text-xs font-semibold text-gray-500 mb-1">Mã kích hoạt (Token một lần):</label>
              <div className="flex items-center justify-between gap-2">
                <code className="text-xs font-mono text-blue-700 break-all select-all">
                  {createdInviteLink.token}
                </code>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => copyToClipboard(createdInviteLink.token)}
                  className="flex-shrink-0 gap-1"
                >
                  <Copy className="h-3.5 w-3.5" />
                  Sao chép
                </Button>
              </div>
            </div>

            <div className="mt-6 flex justify-end">
              <Button onClick={() => setCreatedInviteLink(null)} className="bg-blue-600 hover:bg-blue-700">
                Đã hoàn tất
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

