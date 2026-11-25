'use client';

import Script from 'next/script';
import { useEffect, useRef, useState, type RefObject } from 'react';
import { Button } from '@/components/ui/button';
import { CustomSelect } from '@/components/ui/select';
import { useLocale } from '@/i18n/provider';
import { downloadDriveCsv, downloadSheetTab, GOOGLE_SHEET_MIME, GoogleDriveAuthorizationError, listSheetTabs, type DriveCsvSelection } from '@/infrastructure/google/drive-csv';

const clientId = process.env.NEXT_PUBLIC_GOOGLE_DRIVE_CLIENT_ID;
const apiKey = process.env.NEXT_PUBLIC_GOOGLE_PICKER_API_KEY;
const appId = process.env.NEXT_PUBLIC_GOOGLE_DRIVE_PROJECT_NUMBER;

type Picker = { setVisible: (visible: boolean) => void };
export type GoogleDriveSession = { token: string; expiresAt: number };
type PickerResponse = { action: string; docs?: DriveCsvSelection[] };
type PickerView = {
  setIncludeFolders: (include: boolean) => PickerView;
  setMimeTypes: (types: string) => PickerView;
};
type PickerBuilder = {
  addView: (view: PickerView) => PickerBuilder;
  setAppId: (id: string) => PickerBuilder;
  setOrigin: (origin: string) => PickerBuilder;
  setOAuthToken: (token: string) => PickerBuilder;
  setDeveloperKey: (key: string) => PickerBuilder;
  setCallback: (callback: (response: PickerResponse) => void) => PickerBuilder;
  build: () => Picker;
};
type GoogleApis = {
  gapi?: { load: (name: string, handlers: {
    callback: () => void;
    onerror: () => void;
    ontimeout: () => void;
    timeout: number;
  }) => void };
  google?: {
    accounts?: { oauth2: { initTokenClient: (options: {
      client_id: string;
      scope: string;
      include_granted_scopes: boolean;
      callback: (response: { access_token?: string; expires_in?: string | number; error?: string }) => void;
      error_callback: (error: { type: string }) => void;
    }) => { requestAccessToken: (options?: { prompt: string }) => void } } };
    picker?: {
      DocsView: new (viewId: string) => PickerView;
      PickerBuilder: new () => PickerBuilder;
      ViewId: { DOCS: string };
      Action: { PICKED: string; CANCEL: string; ERROR: string };
    };
  };
};

function apis() {
  return window as Window & GoogleApis;
}

export function DriveCsvPicker({
  disabled, onFile, onBusy, onError, buttonLabel = 'Chọn file từ Google Drive', allowAccountSwitch = false, session,
}: {
  disabled: boolean;
  buttonLabel?: string;
  allowAccountSwitch?: boolean;
  session?: RefObject<GoogleDriveSession | null>;
  onFile: (file: File) => Promise<void>;
  onBusy: (busy: boolean) => void;
  onError: (message: string) => void;
}) {
  const { t } = useLocale();
  const [identityReady, setIdentityReady] = useState(false);
  const [pickerReady, setPickerReady] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [sheetChoice, setSheetChoice] = useState<{
    name: string; tabs: string[]; load: (tab: string) => Promise<void>; cancel: () => void;
  }>();
  const [tab, setTab] = useState('');
  const [loadingTab, setLoadingTab] = useState(false);
  const active = useRef<AbortController | null>(null);
  const visible = useRef<Picker | null>(null);
  const localSession = useRef<GoogleDriveSession | null>(null);
  const driveSession = session ?? localSession;

  useEffect(() => () => {
    active.current?.abort();
    visible.current?.setVisible(false);
  }, []);

  function open(selectAccount = false) {
    const sdk = apis().google;
    if (!clientId || !apiKey || !appId || !sdk?.accounts || !sdk.picker || active.current) return;
    const picker = sdk.picker;
    const pickerConfig = { appId, apiKey };
    const run = new AbortController();
    active.current = run;
    onBusy(true);
    onError('');
    const finish = (message?: string) => {
      if (run.signal.aborted || active.current !== run) return;
      active.current = null;
      visible.current = null;
      setSheetChoice(undefined);
      onBusy(false);
      if (message) onError(message);
    };
    function forgetToken(token: string) {
      if (driveSession.current?.token === token) driveSession.current = null;
    }

    function finishFileError(failure: unknown, token: string, fallback: string) {
      if (run.signal.aborted || active.current !== run) return;
      if (failure instanceof GoogleDriveAuthorizationError) forgetToken(token);
      finish(failure instanceof Error && failure.name === 'Error' ? failure.message : fallback);
    }

    function showPicker(token: string, reused = false) {
      try {
        const view = new picker.DocsView(picker.ViewId.DOCS)
          .setIncludeFolders(true)
          .setMimeTypes('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,text/csv,application/csv,application/octet-stream,text/plain,' + GOOGLE_SHEET_MIME);
        visible.current = new picker.PickerBuilder()
          .addView(view).setAppId(pickerConfig.appId).setOrigin(window.location.origin)
          .setOAuthToken(token).setDeveloperKey(pickerConfig.apiKey)
          .setCallback(async ({ action, docs }) => {
            if (run.signal.aborted || active.current !== run) return;
            if (action === picker.Action.CANCEL) return finish();
            if (action === picker.Action.ERROR) {
              if (reused) forgetToken(token);
              return finish('Không mở được bộ chọn Google Drive. Vui lòng thử lại.');
            }
            if (action !== picker.Action.PICKED) return;
            const selection = docs?.[0];
            if (!selection?.id || !selection.name) return finish('Hãy chọn file Excel (.xlsx), CSV hoặc Google Sheets trên Drive.');
            try {
              if (selection.mimeType === GOOGLE_SHEET_MIME) {
                const tabs = await listSheetTabs(selection, token, AbortSignal.any([run.signal, AbortSignal.timeout(20000)]));
                if (run.signal.aborted || active.current !== run) return;
                if (!tabs.length) return finish('Bảng tính không có tab dữ liệu để nhập.');
                setTab(tabs[0]);
                setSheetChoice({
                  name: selection.name, tabs, cancel: () => finish(),
                  load: async (title) => {
                    try {
                      const file = await downloadSheetTab(selection, title, token,
                        AbortSignal.any([run.signal, AbortSignal.timeout(20000)]));
                      if (!run.signal.aborted) await onFile(file);
                      finish();
                    } catch (failure) {
                      finishFileError(failure, token, 'Không thể đọc Google Sheets. Hãy chọn lại bảng tính và thử lại.');
                    }
                  },
                });
                return;
              }
              const file = await downloadDriveCsv(selection, token, AbortSignal.any([run.signal, AbortSignal.timeout(20000)]));
              if (!run.signal.aborted) await onFile(file);
              finish();
            } catch (failure) {
              finishFileError(failure, token, 'Không thể tải file từ Google Drive. Vui lòng thử lại.');
            }
          })
          .build();
        visible.current.setVisible(true);
      } catch {
        finish('Không mở được bộ chọn Google Drive. Vui lòng thử lại.');
      }
    }

    const cached = driveSession.current;
    if (!selectAccount && cached && cached.expiresAt > Date.now() + 30_000) {
      showPicker(cached.token, true);
      return;
    }

    try {
      sdk.accounts.oauth2.initTokenClient({
        client_id: clientId,
        scope: 'https://www.googleapis.com/auth/drive.file',
        include_granted_scopes: false,
        error_callback: ({ type }) => finish(type === 'popup_closed' ? undefined :
          'Không mở được đăng nhập Google. Hãy cho phép cửa sổ bật lên rồi thử lại.'),
        callback: ({ access_token: token, expires_in, error }) => {
          if (run.signal.aborted || active.current !== run) return;
          if (error || !token) {
            finish('Không thể kết nối Google Drive. Hãy kiểm tra quyền và thử lại.');
            return;
          }
          const lifetime = Number(expires_in);
          driveSession.current = Number.isFinite(lifetime) && lifetime > 0
            ? { token, expiresAt: Date.now() + lifetime * 1000 }
            : null;
          showPicker(token);
        },
      }).requestAccessToken({ prompt: selectAccount ? 'select_account' : '' });
    } catch {
      finish('Không thể kết nối Google Drive. Hãy kiểm tra quyền và thử lại.');
    }
  }

  if (!clientId || !apiKey || !appId) {
    return <p className="text-xs text-slate-600">{t('Google Drive chưa được cấu hình.')}</p>;
  }

  return (
    <div>
      <Script src="https://accounts.google.com/gsi/client" onReady={() => setIdentityReady(true)}
        onError={() => setLoadFailed(true)} />
      <Script src="https://apis.google.com/js/api.js" onReady={() => {
        apis().gapi?.load('picker', {
          callback: () => setPickerReady(true),
          onerror: () => setLoadFailed(true),
          ontimeout: () => setLoadFailed(true),
          timeout: 10000,
        });
      }} onError={() => setLoadFailed(true)} />
      <div className="flex flex-wrap gap-2">
        <Button size="sm" variant="outline" type="button"
          disabled={disabled || loadFailed || !identityReady || !pickerReady} onClick={() => open()}>
          {t(buttonLabel)}
        </Button>
        {allowAccountSwitch && (
          <Button size="sm" variant="ghost" type="button"
            disabled={disabled || loadFailed || !identityReady || !pickerReady} onClick={() => open(true)}>
            {t('Đổi tài khoản Google')}
          </Button>
        )}
      </div>
      {sheetChoice && (
        <div className="mt-3 space-y-2 rounded-md border border-slate-200 p-3">
          <p className="break-all text-sm font-medium">{sheetChoice.name}</p>
          <label className="block text-sm">
            {t('Tab Google Sheets')}
            <CustomSelect
              className="mt-1"
              value={tab}
              disabled={loadingTab}
              onValueChange={setTab}
              options={sheetChoice.tabs.map((title) => ({ value: title, label: title }))}
            />
          </label>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" type="button" isLoading={loadingTab} onClick={async () => {
              setLoadingTab(true);
              try { await sheetChoice.load(tab); } finally { setLoadingTab(false); }
            }}>{t('Dùng tab này')}</Button>
            <Button size="sm" type="button" variant="outline" disabled={loadingTab} onClick={sheetChoice.cancel}>{t('Hủy')}</Button>
          </div>
        </div>
      )}
      {loadFailed && <p role="alert" className="mt-1 text-xs text-red-700">
        {t('Không tải được Google Drive. Hãy tải lại trang hoặc chọn CSV từ máy.')}
      </p>}
    </div>
  );
}
