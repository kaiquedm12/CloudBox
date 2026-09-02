"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

export type Language = "pt-BR" | "en";

const messages = {
  "pt-BR": {
    overview: "Visão geral",
    containers: "Containers",
    signIn: "Entrar",
    signingIn: "Entrando...",
    signOut: "Sair",
    signingOut: "Saindo...",
    mainNavigation: "Navegação principal",
    homeLabel: "CloudBox — página inicial",
    switchToEnglish: "Mudar idioma para inglês",
    switchToPortuguese: "Mudar idioma para português",
    lightMode: "Ativar modo claro",
    darkMode: "Ativar modo escuro",
    lightTheme: "Usar tema claro",
    darkTheme: "Usar tema escuro",
    secureAccess: "Acesso seguro",
    loginDescription: "Entre com suas credenciais para acessar o painel de gerenciamento da CloudBox.",
    password: "Senha",
    passwordPlaceholder: "Digite sua senha",
    emailPlaceholder: "voce@empresa.com",
    loginFailed: "Não foi possível realizar o login.",
    clusterNodes: "Nós do cluster",
    clusterDescription: "Acompanhe a disponibilidade e os recursos de todos os nós registrados no orquestrador.",
    updatedAt: "Atualizado às",
    awaitingFirstRead: "Aguardando primeira leitura",
    realtimeConnected: "Conectado em tempo real",
    realtimeShortConnected: "Tempo real conectado",
    reconnecting: "Reconectando...",
    connecting: "Conectando...",
    loadingNodes: "Carregando nós do cluster",
    orchestratorError: "Não foi possível conectar ao orquestrador",
    orchestratorHelp: "Verifique se o serviço está em execução e se a URL configurada está acessível.",
    tryAgain: "Tentar novamente",
    registeredNode: "nó registrado",
    registeredNodes: "nós registrados",
    clusterSummary: "Resumo do cluster",
    websocketUpdates: "Atualizações recebidas via WebSocket",
    staleData: "A última atualização falhou. Os dados exibidos podem estar desatualizados.",
    updateNow: "Atualizar agora",
    registeredNodesLabel: "Nós registrados",
    noNodes: "Nenhum nó registrado",
    noNodesDescription: "Assim que um agente se registrar no orquestrador, seus recursos aparecerão automaticamente nesta tela.",
    nodeAbbreviation: "NÓ",
    clusterNode: "Nó do cluster",
    viewNode: "Ver detalhes do nó",
    heartbeatMissing: "Heartbeat ainda não recebido",
    heartbeatUnavailable: "Horário do heartbeat indisponível",
    lastHeartbeatAt: "Último heartbeat em",
    available: "disponível",
    freeCpu: "CPU livre",
    freeRam: "RAM livre",
    freeDisk: "Disco livre",
    cores: "núcleos",
    temperature: "Temperatura",
    noSensor: "Sem sensor",
    loadingNode: "Carregando dados do nó...",
    loadNodeFailed: "Não foi possível carregar o nó",
    nodeNotFound: "Nó não encontrado",
    nodeNotFoundDescription: "O identificador informado não corresponde a um nó registrado.",
    backOverview: "Voltar para a visão geral",
    currentMetrics: "Métricas atuais",
    availableCpu: "CPU disponível",
    availableRam: "RAM disponível",
    availableDisk: "Disco disponível",
    of: "de",
    notReceived: "Ainda não recebido",
    dateUnavailable: "Data indisponível",
    lastHeartbeat: "Último heartbeat",
    allocatedContainers: "Containers alocados",
    onThisNode: "neste nó",
    loadingContainers: "Carregando containers...",
    loadNodeContainersFailed: "Não foi possível carregar os containers deste nó.",
    noNodeContainers: "Este nó ainda não possui containers alocados.",
    containersDescription: "Solicite novas cargas e acompanhe o status dos containers agendados no cluster.",
    newContainer: "Novo container",
    loadContainersFailed: "Não foi possível carregar os containers",
    noContainers: "Nenhum container encontrado.",
    image: "Imagem",
    node: "Nó",
    status: "Status",
    resources: "Recursos",
    createdAt: "Criado em",
    actions: "Ações",
    awaiting: "Aguardando",
    stop: "Parar",
    remove: "Remover",
    pending: "Pendente",
    scheduled: "Agendado",
    running: "Em execução",
    stopping: "Parando",
    stopped: "Parado",
    removing: "Removendo",
    removed: "Removido",
    error: "Erro",
    failed: "Falhou",
    scheduler: "Agendador CloudBox",
    close: "Fechar",
    createSuccess: "Container criado com sucesso",
    imageAllocated: "A imagem",
    allocatedOnNode: "foi alocada no nó",
    selected: "selecionado",
    finish: "Concluir",
    dockerImage: "Imagem Docker",
    requestedCpus: "CPUs solicitadas",
    requestedRam: "RAM solicitada (MB)",
    requestedDisk: "Disco solicitado (MB)",
    cancel: "Cancelar",
    requesting: "Solicitando...",
    requestContainer: "Solicitar container",
    capacityError: "Não há nenhum nó online com CPU e RAM suficientes para esta solicitação. Reduza os recursos ou tente novamente mais tarde.",
    requestFailed: "Não foi possível solicitar o container.",
    invalidImage: "Informe uma imagem Docker válida.",
    invalidCpu: "Informe uma quantidade válida de CPUs.",
    invalidRam: "Informe uma quantidade válida de RAM.",
    invalidDisk: "Informe uma quantidade válida de disco.",
  },
  en: {
    overview: "Overview", containers: "Containers", signIn: "Sign in", signingIn: "Signing in...", signOut: "Sign out", signingOut: "Signing out...",
    mainNavigation: "Main navigation", homeLabel: "CloudBox — home page", switchToEnglish: "Switch language to English", switchToPortuguese: "Switch language to Portuguese",
    lightMode: "Enable light mode", darkMode: "Enable dark mode", lightTheme: "Use light theme", darkTheme: "Use dark theme",
    secureAccess: "Secure access", loginDescription: "Sign in with your credentials to access the CloudBox management dashboard.", password: "Password", passwordPlaceholder: "Enter your password", emailPlaceholder: "you@company.com", loginFailed: "Unable to sign in.",
    clusterNodes: "Cluster nodes", clusterDescription: "Monitor the availability and resources of every node registered with the orchestrator.", updatedAt: "Updated at", awaitingFirstRead: "Waiting for the first reading",
    realtimeConnected: "Connected in real time", realtimeShortConnected: "Real-time connected", reconnecting: "Reconnecting...", connecting: "Connecting...", loadingNodes: "Loading cluster nodes",
    orchestratorError: "Unable to connect to the orchestrator", orchestratorHelp: "Check that the service is running and that the configured URL is accessible.", tryAgain: "Try again",
    registeredNode: "registered node", registeredNodes: "registered nodes", clusterSummary: "Cluster summary", websocketUpdates: "Updates received via WebSocket", staleData: "The latest update failed. The displayed data may be outdated.", updateNow: "Update now",
    registeredNodesLabel: "Registered nodes", noNodes: "No registered nodes", noNodesDescription: "As soon as an agent registers with the orchestrator, its resources will automatically appear here.", nodeAbbreviation: "NODE",
    clusterNode: "Cluster node", viewNode: "View node details", heartbeatMissing: "Heartbeat not received yet", heartbeatUnavailable: "Heartbeat time unavailable", lastHeartbeatAt: "Last heartbeat at", available: "available",
    freeCpu: "Free CPU", freeRam: "Free RAM", freeDisk: "Free disk", cores: "cores", temperature: "Temperature", noSensor: "No sensor",
    loadingNode: "Loading node data...", loadNodeFailed: "Unable to load the node", nodeNotFound: "Node not found", nodeNotFoundDescription: "The provided identifier does not match a registered node.", backOverview: "Back to overview",
    currentMetrics: "Current metrics", availableCpu: "Available CPU", availableRam: "Available RAM", availableDisk: "Available disk", of: "of", notReceived: "Not received yet", dateUnavailable: "Date unavailable", lastHeartbeat: "Last heartbeat",
    allocatedContainers: "Allocated containers", onThisNode: "on this node", loadingContainers: "Loading containers...", loadNodeContainersFailed: "Unable to load this node's containers.", noNodeContainers: "This node does not have any allocated containers yet.",
    containersDescription: "Request new workloads and monitor the status of containers scheduled in the cluster.", newContainer: "New container", loadContainersFailed: "Unable to load containers", noContainers: "No containers found.",
    image: "Image", node: "Node", status: "Status", resources: "Resources", createdAt: "Created at", actions: "Actions", awaiting: "Waiting", stop: "Stop", remove: "Remove",
    pending: "Pending", scheduled: "Scheduled", running: "Running", stopping: "Stopping", stopped: "Stopped", removing: "Removing", removed: "Removed", error: "Error", failed: "Failed",
    scheduler: "CloudBox scheduler", close: "Close", createSuccess: "Container created successfully", imageAllocated: "Image", allocatedOnNode: "was allocated to node", selected: "selected", finish: "Done",
    dockerImage: "Docker image", requestedCpus: "Requested CPUs", requestedRam: "Requested RAM (MB)", requestedDisk: "Requested disk (MB)", cancel: "Cancel", requesting: "Requesting...", requestContainer: "Request container",
    capacityError: "There are no online nodes with enough CPU and RAM for this request. Reduce the requested resources or try again later.", requestFailed: "Unable to request the container.",
    invalidImage: "Enter a valid Docker image.", invalidCpu: "Enter a valid CPU amount.", invalidRam: "Enter a valid RAM amount.", invalidDisk: "Enter a valid disk amount.",
  },
} as const;

export type MessageKey = keyof (typeof messages)["pt-BR"];

type LanguageContextValue = {
  language: Language;
  locale: string;
  setLanguage: (language: Language) => void;
  t: (key: MessageKey) => string;
};

const LanguageContext = createContext<LanguageContextValue | null>(null);

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<Language>("pt-BR");

  useEffect(() => {
    const savedLanguage = localStorage.getItem("cloudbox-language");
    if (savedLanguage === "en" || savedLanguage === "pt-BR") {
      setLanguageState(savedLanguage);
    }
  }, []);

  function setLanguage(nextLanguage: Language) {
    setLanguageState(nextLanguage);
    localStorage.setItem("cloudbox-language", nextLanguage);
    document.documentElement.lang = nextLanguage;
  }

  useEffect(() => {
    document.documentElement.lang = language;
  }, [language]);

  const value = useMemo<LanguageContextValue>(
    () => ({
      language,
      locale: language === "en" ? "en-US" : "pt-BR",
      setLanguage,
      t: (key) => messages[language][key],
    }),
    [language],
  );

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useLanguage() {
  const context = useContext(LanguageContext);
  if (!context) throw new Error("useLanguage must be used within LanguageProvider.");
  return context;
}
