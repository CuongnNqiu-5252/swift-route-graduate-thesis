'use client';

import { useEffect, useState } from 'react';

import { ProofPolicy, TenantDeliveryProofConfig } from '@/domain/entities/tenant-settings.entity';
import { tenantSettingsRepository } from '@/infrastructure/repositories/tenant-settings.repository';
import {
    Camera,
    FileText,
    Info,
    Loader2,
    PenLine,
} from 'lucide-react';

type ProofField = keyof TenantDeliveryProofConfig;

const rows = [
    {
        label: 'Giao hàng thành công',
        fields: [
            'success_note_policy',
            'success_signature_policy',
            'success_photo_policy',
        ],
    },
    {
        label: 'Giao hàng thất bại',
        fields: [
            'failed_note_policy',
            'failed_signature_policy',
            'failed_photo_policy',
        ],
    },
] as const;

const columns = [
    { label: 'Ghi chú', icon: FileText },
    { label: 'Chữ ký', icon: PenLine },
    { label: 'Hình ảnh', icon: Camera },
];

const policies = [
    { value: 'DISABLED', label: 'Vô hiệu hóa' },
    { value: 'OPTIONAL', label: 'Không bắt buộc' },
    { value: 'REQUIRED', label: 'Bắt buộc' },
] as const;

export default function ProofOfDeliveryPage() {
    const [config, setConfig] =
        useState<TenantDeliveryProofConfig | null>(null);

    const [savedConfig, setSavedConfig] =
        useState<TenantDeliveryProofConfig | null>(null);

    const [loading, setLoading] = useState(true);

    const [saving, setSaving] = useState(false);

    const [error, setError] = useState<string | null>(null);

    // Kiểm tra cấu hình có thay đổi không
    const isDirty =
        config !== null &&
        savedConfig !== null &&
        rows.some(({ fields }) =>
            fields.some((field) => config[field] !== savedConfig[field])
        );

    // Lấy cấu hình khi mở trang
    useEffect(() => {
        let active = true;

        async function fetchConfig() {
            try {
                setLoading(true);
                setError(null);

                const data =
                    await tenantSettingsRepository.getDeliveryProofConfig();

                if (!active) return;

                setConfig(data);
                setSavedConfig(data);
            } catch (error) {
                if (!active) return;

                console.error(error);
                setError('Không thể tải cấu hình bằng chứng giao nhận.');
            } finally {
                if (active) setLoading(false);
            }
        }

        fetchConfig();

        return () => {
            active = false;
        };
    }, []);

    // Thay đổi một policy
    function handleChange(
        field: ProofField,
        value: ProofPolicy
    ) {
        setConfig((prev) => {
            if (!prev) return prev;

            return {
                ...prev,
                [field]: value,
            };
        });
    }

    // Lưu cấu hình
    async function handleSave() {
        if (!config || !isDirty || saving) return;

        try {
            setSaving(true);
            setError(null);

            const updatedConfig =
                await tenantSettingsRepository.updateDeliveryProofConfig(
                    config
                );

            setConfig(updatedConfig);
            setSavedConfig(updatedConfig);
        } catch (error) {
            console.error(error);
            setError('Không thể lưu cấu hình. Vui lòng thử lại.');
        } finally {
            setSaving(false);
        }
    }

    // Khôi phục cấu hình trước khi chỉnh sửa
    function handleReset() {
        setConfig(savedConfig);
        setError(null);
    }

    if (loading) {
        return (
            <div className="flex h-64 items-center justify-center">
                <Loader2 className="size-6 animate-spin text-blue-600" />
            </div>
        );
    }

    if (!config) {
        return (
            <div className="p-6 text-sm text-red-600">
                {error ?? 'Không tìm thấy cấu hình.'}
            </div>
        );
    }

    return (
        <div className="space-y-6 p-6">

            {/* Header */}
            <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                    <h1 className="text-xl font-semibold text-slate-900">
                        Bằng chứng giao nhận
                    </h1>

                    <p className="mt-1 text-sm text-slate-500">
                        Cấu hình yêu cầu xác nhận giao nhận đơn hàng.
                    </p>
                </div>

                <div className="flex items-center gap-2">

                    <button
                        onClick={handleReset}
                        disabled={!isDirty || saving}
                        className="rounded-lg border px-4 py-2 text-sm
                       hover:bg-slate-50 disabled:opacity-50"
                    >
                        Hủy
                    </button>

                    <button
                        onClick={handleSave}
                        disabled={!isDirty || saving}
                        className="flex items-center gap-2 rounded-lg
                       bg-blue-600 px-4 py-2 text-sm
                       font-medium text-white hover:bg-blue-700
                       disabled:cursor-not-allowed
                       disabled:opacity-50"
                    >
                        {saving && (
                            <Loader2 className="size-4 animate-spin" />
                        )}

                        {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
                    </button>

                </div>
            </div>

            {/* Description */}
            <div className="flex items-start gap-3 rounded-lg
                      border border-blue-100 bg-blue-50 p-4">

                <Info className="size-5 shrink-0 text-blue-600" />

                <p className="text-sm text-blue-800">
                    Tài xế không thể hoàn thành đơn hàng nếu chưa
                    cung cấp đầy đủ các bằng chứng bắt buộc.
                </p>

            </div>

            {/* Error */}
            {error && (
                <div role="alert"
                    className="rounded-lg border border-red-200
                        bg-red-50 p-3 text-sm text-red-600">
                    {error}
                </div>
            )}

            {/* Configuration table */}
            <div className="overflow-x-auto rounded-xl
                      border border-slate-200 bg-white">

                <table className="w-full min-w-[720px] text-sm">

                    <thead className="border-b bg-slate-50">
                        <tr>

                            <th className="px-5 py-4 text-left font-medium">
                                Kết quả giao hàng
                            </th>

                            {columns.map(({ label, icon: Icon }) => (
                                <th key={label} className="px-5 py-4 font-medium">

                                    <div className="flex items-center
                                  justify-center gap-2">
                                        <Icon className="size-4 text-slate-500" />
                                        {label}
                                    </div>

                                </th>
                            ))}

                        </tr>
                    </thead>

                    <tbody className="divide-y divide-slate-100">

                        {rows.map(({ label, fields }) => (
                            <tr key={label}>

                                <td className="px-5 py-5 font-medium text-slate-700">
                                    {label}
                                </td>

                                {fields.map((field, index) => (
                                    <td key={field} className="px-5 py-5">

                                        <select
                                            aria-label={`${label} - ${columns[index].label}`}
                                            value={config[field]}
                                            disabled={saving}
                                            onChange={(e) =>
                                                handleChange(
                                                    field,
                                                    e.target.value as ProofPolicy
                                                )
                                            }
                                            className="w-full cursor-pointer rounded-lg
                                 border border-slate-300 bg-white
                                 px-3 py-2 outline-none
                                 focus:border-blue-500
                                 disabled:opacity-50"
                                        >
                                            {policies.map(({ value, label }) => (
                                                <option key={value} value={value}>
                                                    {label}
                                                </option>
                                            ))}
                                        </select>

                                    </td>
                                ))}

                            </tr>
                        ))}

                    </tbody>

                </table>

            </div>

        </div>
    );
}
