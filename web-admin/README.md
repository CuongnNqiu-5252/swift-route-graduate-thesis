This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

### Workspace AI assistant

Create a key in [Google AI Studio](https://aistudio.google.com/app/apikey) and add it to the repository root `.env`.

```dotenv
GEMINI_API_KEY=your_gemini_api_key
GEMINI_MODEL=gemini-3.1-flash-lite
```

The model setting is optional. Docker Compose passes both values to Order Service. Rebuild and restart that service with `docker compose up -d --build order-service`. When running Order Service directly, set the same variables in its process environment.

Open **AI** in the tenant workspace header. The browser posts the message to the authenticated gateway and reads the reply as SSE. The key stays in Order Service.

Gemini selects Spring AI tools for waiting orders by depot, delivery appointment risk, route handoffs or the selected route. An out-of-scope request receives a fixed reply without loading tenant data. Only the tools available to the signed-in user's permissions are offered. Their results supply the facts and route links in the answer.

To add a read tool, put the tenant-scoped query and compact result in `AssistantToolData`, then register it with a distinct tool name and description in `AssistantTools.forScope`. The request scope comes from the authenticated controller. The model chooses from the registered tools and receives only the results of the selected tools.

### Goong Maps

Create `web-admin/.env.local` before running the Web Admin:

```dotenv
GOONG_API_KEY=your_goong_rest_api_key
NEXT_PUBLIC_GOONG_MAP_KEY=your_goong_map_key
```

`GOONG_API_KEY` is only read by Next.js Route Handlers for address search and reverse geocoding. The browser only receives the map display key.

### Dispatch map layers

Open **Dispatch → Map layers** to toggle the weather forecast and live traffic.
Weather shows the chance of rain at up to ten delivery stops, including 0%.
Blue markers highlight a 40% or higher chance of rain. A route preview uses
the estimated arrival if you leave now.
A route in progress shows the current forecast hour.

Add `TOMTOM_TRAFFIC_API_KEY` to `web-admin/.env.local` to enable TomTom Orbis
Traffic Flow. The key stays on the Next.js server. Roads without traffic data
remain unmarked. Check coverage on the roads used by your tenant before rollout.
For non-commercial weather demos Open-Meteo works without a key. Set
`OPEN_METEO_API_KEY` for the customer endpoint under a commercial subscription.

### Import CSV or Google Sheets from Drive

1. In one Google Cloud project, enable **Google Picker API**, **Google Drive API**, and **Google Sheets API**. Configure the OAuth consent screen with the `https://www.googleapis.com/auth/drive.file` scope and add your Google account as a test user if the app is in testing mode.
2. Create an OAuth client of type **Web application**. Add `http://localhost:3000` to **Authorized JavaScript origins**. If you use another host or port, add the exact browser origin.
3. Create a browser API key. Under website restrictions, allow `http://localhost:3000/*` and `https://docs.google.com/*`. Restrict the key to Google Picker API and Google Drive API. Find the numeric **Project number** in the project settings.
4. Add these values to `web-admin/.env.local` and restart the development server:

```dotenv
NEXT_PUBLIC_GOOGLE_DRIVE_CLIENT_ID=your_oauth_client_id
NEXT_PUBLIC_GOOGLE_PICKER_API_KEY=your_restricted_browser_key
NEXT_PUBLIC_GOOGLE_DRIVE_PROJECT_NUMBER=your_numeric_project_number
```

Open **Orders → Add orders → Import CSV → Choose file from Google Drive**, or use **Prepare CSV from Drive** in Operation Flow. Select an uploaded `.csv` file or a Google spreadsheet. For a spreadsheet, choose the tab to preview. Use the same headers as the CSV template. In Google Sheets, format source codes, phone numbers, and ISO timestamps as plain text so their displayed values are preserved. Use a decimal point for numeric columns. This flow uses a short-lived browser access token and does not require a client secret.

See the [Google Picker setup guide](https://developers.google.com/workspace/drive/picker/guides/web-picker).

## Calling APIs from the web app

Pages, components, and hooks call feature repositories in `src/infrastructure/repositories` (or `goongMapsClient` for map lookups). Keep endpoint paths, request payloads, and response mapping in those modules. `src/lib/api.ts` owns Axios, gateway authentication and tenant headers, refresh, and HTTP error normalization. The local maps proxy returns raw data and does not receive gateway credentials.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
