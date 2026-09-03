import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement>;

function IconBase({ children, ...props }: IconProps) {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.8"
      viewBox="0 0 24 24"
      {...props}
    >
      {children}
    </svg>
  );
}

export function ActivityIcon(props: IconProps) {
  return <IconBase {...props}><path d="M3 12h4l2-6 4 12 2-6h6" /></IconBase>;
}

export function AlertIcon(props: IconProps) {
  return <IconBase {...props}><path d="M12 9v4m0 4h.01" /><path d="M10.3 3.7 2.6 17a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 3.7a2 2 0 0 0-3.4 0Z" /></IconBase>;
}

export function ArrowRightIcon(props: IconProps) {
  return <IconBase {...props}><path d="m9 18 6-6-6-6" /></IconBase>;
}

export function BoxIcon(props: IconProps) {
  return <IconBase {...props}><path d="m21 8-9 5-9-5m9 5v9" /><path d="m3.3 7 8.7-5 8.7 5v10L12 22l-8.7-5Z" /></IconBase>;
}

export function CheckCircleIcon(props: IconProps) {
  return <IconBase {...props}><circle cx="12" cy="12" r="9" /><path d="m8 12 2.5 2.5L16 9" /></IconBase>;
}

export function GridIcon(props: IconProps) {
  return <IconBase {...props}><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></IconBase>;
}

export function ListIcon(props: IconProps) {
  return <IconBase {...props}><path d="M8 6h13M8 12h13M8 18h13" /><path d="M3 6h.01M3 12h.01M3 18h.01" /></IconBase>;
}

export function MenuIcon(props: IconProps) {
  return <IconBase {...props}><path d="M4 7h16M4 12h16M4 17h16" /></IconBase>;
}

export function PlusIcon(props: IconProps) {
  return <IconBase {...props}><path d="M12 5v14M5 12h14" /></IconBase>;
}

export function RefreshIcon(props: IconProps) {
  return <IconBase {...props}><path d="M20 7v5h-5" /><path d="M4 17v-5h5" /><path d="M6.1 9a7 7 0 0 1 11.4-2L20 9M4 15l2.5 2A7 7 0 0 0 17.9 15" /></IconBase>;
}

export function SearchIcon(props: IconProps) {
  return <IconBase {...props}><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></IconBase>;
}

export function ServerIcon(props: IconProps) {
  return <IconBase {...props}><rect x="3" y="4" width="18" height="6" rx="2" /><rect x="3" y="14" width="18" height="6" rx="2" /><path d="M7 7h.01M7 17h.01" /></IconBase>;
}

export function XIcon(props: IconProps) {
  return <IconBase {...props}><path d="m6 6 12 12M18 6 6 18" /></IconBase>;
}
