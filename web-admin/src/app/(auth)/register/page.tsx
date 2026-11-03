'use client';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import api from '@/lib/api';
import { zodResolver } from '@hookform/resolvers/zod';
import { AlertCircle, CheckCircle2, Truck } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import * as z from 'zod';

const registerSchema = z
  .object({
    fullName: z.string().min(2, 'Họ và tên tối thiểu 2 ký tự'),
    phoneNumber: z.string().min(9, 'Số điện thoại không hợp lệ'),
    email: z.string().email('Email không đúng định dạng'),
    password: z.string().min(6, 'Mật khẩu tối thiểu 6 ký tự'),
    confirmPassword: z.string().min(6, 'Xác nhận mật khẩu tối thiểu 6 ký tự'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Mật khẩu xác nhận không khớp',
    path: ['confirmPassword'],
  });

type RegisterFormValues = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [isSuccess, setIsSuccess] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
  });

  const onSubmit = async (values: RegisterFormValues) => {
    setIsLoading(true);
    setError(null);
    try {
      await api.post('/auth/register', {
        email: values.email.trim(),
        password: values.password,
        full_name: values.fullName.trim(),
        phone_number: values.phoneNumber.trim(),
        address: 'District 1',
        city: 'Ho Chi Minh',
        state: 'VN',
        zip_code: '70000',
        country: 'VN',
      });

      setIsSuccess(true);
      toast.success('Đăng ký tài khoản thành công!');
      setTimeout(() => {
        router.push('/login');
      }, 1500);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: { message?: string } } }; message?: string };
      const msg = axiosErr.response?.data?.error?.message || axiosErr.message || 'Đăng ký thất bại';
      setError(msg);
      toast.error(msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-blue-50/30 to-indigo-50/40 p-4">
      <div className="w-full max-w-md">
        {/* Brand header */}
        <div className="mb-8 text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-blue-600 text-white shadow-lg shadow-blue-500/30">
            <Truck size={26} />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900">SwiftRoute</h1>
          <p className="mt-1 text-sm text-gray-500">
            Hệ thống Điều phối & Quản trị Logistics Thông minh
          </p>
        </div>

        <Card className="border border-gray-200/80 shadow-xl">
          <CardHeader className="pb-4">
            <CardTitle className="text-lg font-bold text-gray-800">Đăng ký tài khoản doanh nghiệp</CardTitle>
          </CardHeader>
          <CardContent>
            {isSuccess ? (
              <div className="space-y-4 py-4 text-center">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-green-100 text-green-600">
                  <CheckCircle2 size={28} />
                </div>
                <h3 className="text-base font-semibold text-gray-900">Đăng ký thành công!</h3>
                <p className="text-xs text-gray-500">
                  Tài khoản của bạn đã được khởi tạo. Đang chuyển hướng sang trang đăng nhập...
                </p>
                <Link
                  href="/login"
                  className="inline-block text-sm font-semibold text-blue-600 hover:text-blue-700 underline"
                >
                  Đăng nhập ngay
                </Link>
              </div>
            ) : (
              <form onSubmit={handleSubmit(onSubmit)} className="space-y-3.5">
                <div className="relative">
                  <Input
                    id="fullName"
                    type="text"
                    label="Họ và tên"
                    placeholder="Nguyễn Văn A"
                    error={errors.fullName?.message}
                    {...register('fullName')}
                  />
                </div>

                <div className="relative">
                  <Input
                    id="phoneNumber"
                    type="tel"
                    label="Số điện thoại"
                    placeholder="0901234567"
                    error={errors.phoneNumber?.message}
                    {...register('phoneNumber')}
                  />
                </div>

                <div className="relative">
                  <Input
                    id="email"
                    type="email"
                    label="Email công việc"
                    placeholder="admin@swiftroute.vn"
                    error={errors.email?.message}
                    {...register('email')}
                  />
                </div>

                <div className="relative">
                  <Input
                    id="password"
                    type="password"
                    label="Mật khẩu"
                    placeholder="••••••••"
                    error={errors.password?.message}
                    {...register('password')}
                  />
                </div>

                <div className="relative">
                  <Input
                    id="confirmPassword"
                    type="password"
                    label="Xác nhận mật khẩu"
                    placeholder="••••••••"
                    error={errors.confirmPassword?.message}
                    {...register('confirmPassword')}
                  />
                </div>

                {error && (
                  <div className="flex items-center gap-2 rounded-lg bg-red-50 p-3 text-xs text-red-700">
                    <AlertCircle size={15} />
                    <span>{error}</span>
                  </div>
                )}

                <Button
                  type="submit"
                  className="w-full bg-blue-600 hover:bg-blue-700 py-2.5 font-semibold text-white shadow-sm mt-2"
                  isLoading={isLoading}
                >
                  Tạo tài khoản
                </Button>

                <div className="pt-2 text-center text-xs text-gray-500">
                  Đã có tài khoản?{' '}
                  <Link href="/login" className="font-semibold text-blue-600 hover:text-blue-700 underline">
                    Đăng nhập
                  </Link>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

