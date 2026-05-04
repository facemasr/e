import 'dart:async';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ShortSeriesWebViewApp());
}

class AppConfig {
  static const String appName = 'شورت سيريز';
  static const String websiteUrl = 'https://shortseris.online/';
  static const String allowedHost = 'shortseris.online';

  static const Color primaryColor = Color(0xFFFF3D68);
  static const Color backgroundColor = Color(0xFF070A12);
  static const Color navColor = Color(0xFF090D17);
}

class ShortSeriesWebViewApp extends StatelessWidget {
  const ShortSeriesWebViewApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: AppConfig.appName,
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: AppConfig.backgroundColor,
        colorScheme: ColorScheme.fromSeed(
          seedColor: AppConfig.primaryColor,
          brightness: Brightness.dark,
          primary: AppConfig.primaryColor,
          surface: AppConfig.navColor,
        ),
        navigationBarTheme: NavigationBarThemeData(
          backgroundColor: AppConfig.navColor,
          indicatorColor: AppConfig.primaryColor.withOpacity(.16),
          labelTextStyle: WidgetStateProperty.all(
            const TextStyle(fontSize: 12, fontWeight: FontWeight.w700),
          ),
        ),
      ),
      home: const WebViewScreen(),
    );
  }
}

class WebViewScreen extends StatefulWidget {
  const WebViewScreen({super.key});

  @override
  State<WebViewScreen> createState() => _WebViewScreenState();
}

class _WebViewScreenState extends State<WebViewScreen> {
  late final WebViewController _controller;
  StreamSubscription<List<ConnectivityResult>>? _connectivitySubscription;

  bool _isLoading = true;
  bool _hasInternet = true;
  int _navIndex = 2;

  @override
  void initState() {
    super.initState();
    _initWebView();
    _watchInternet();
  }

  void _initWebView() {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(AppConfig.backgroundColor)
      ..enableZoom(false)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (_) => setState(() => _isLoading = true),
          onPageFinished: (_) => setState(() => _isLoading = false),
          onWebResourceError: (_) => setState(() => _isLoading = false),
          onNavigationRequest: (request) async {
            final uri = Uri.tryParse(request.url);
            if (uri == null) return NavigationDecision.prevent;

            final isHttp = uri.scheme == 'http' || uri.scheme == 'https';
            final isSameDomain = uri.host == AppConfig.allowedHost ||
                uri.host.endsWith('.${AppConfig.allowedHost}');

            if (isHttp && isSameDomain) {
              return NavigationDecision.navigate;
            }

            if (isHttp) {
              await launchUrl(uri, mode: LaunchMode.externalApplication);
              return NavigationDecision.prevent;
            }

            return NavigationDecision.prevent;
          },
        ),
      )
      ..loadRequest(Uri.parse(AppConfig.websiteUrl));
  }

  Future<void> _watchInternet() async {
    final initial = await Connectivity().checkConnectivity();
    if (mounted) {
      setState(() => _hasInternet = !initial.contains(ConnectivityResult.none));
    }

    _connectivitySubscription =
        Connectivity().onConnectivityChanged.listen((results) {
      final online = !results.contains(ConnectivityResult.none);
      if (mounted) setState(() => _hasInternet = online);
      if (online) _controller.reload();
    });
  }

  Future<void> _reload() async {
    setState(() => _isLoading = true);
    await _controller.reload();
  }

  Future<bool> _goBackOrExit() async {
    if (await _controller.canGoBack()) {
      await _controller.goBack();
      return false;
    }
    return true;
  }

  Future<void> _handleNav(int index) async {
    setState(() => _navIndex = index);

    switch (index) {
      case 0:
        if (await _controller.canGoBack()) await _controller.goBack();
        break;
      case 1:
        if (await _controller.canGoForward()) await _controller.goForward();
        break;
      case 2:
        await _controller.loadRequest(Uri.parse(AppConfig.websiteUrl));
        break;
      case 3:
        await _reload();
        break;
    }

    await Future<void>.delayed(const Duration(milliseconds: 250));
    if (mounted) setState(() => _navIndex = 2);
  }

  @override
  void dispose() {
    _connectivitySubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.rtl,
      child: PopScope(
        canPop: false,
        onPopInvokedWithResult: (didPop, _) async {
          if (didPop) return;
          final shouldExit = await _goBackOrExit();
          if (shouldExit && context.mounted) {
            Navigator.of(context).maybePop();
          }
        },
        child: Scaffold(
          body: SafeArea(
            child: Stack(
              children: [
                if (_hasInternet)
                  WebViewWidget(controller: _controller)
                else
                  NoInternetView(onRetry: _reload),
                if (_isLoading && _hasInternet)
                  const Align(
                    alignment: Alignment.topCenter,
                    child: LinearProgressIndicator(
                      color: AppConfig.primaryColor,
                      backgroundColor: Colors.transparent,
                      minHeight: 3,
                    ),
                  ),
              ],
            ),
          ),
          bottomNavigationBar: NavigationBar(
            selectedIndex: _navIndex,
            onDestinationSelected: _handleNav,
            destinations: const [
              NavigationDestination(
                icon: Icon(Icons.arrow_back_ios_new_rounded),
                label: 'رجوع',
              ),
              NavigationDestination(
                icon: Icon(Icons.arrow_forward_ios_rounded),
                label: 'تقدم',
              ),
              NavigationDestination(
                icon: Icon(Icons.home_outlined),
                selectedIcon: Icon(Icons.home_rounded),
                label: 'الرئيسية',
              ),
              NavigationDestination(
                icon: Icon(Icons.refresh_rounded),
                label: 'تحديث',
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class NoInternetView extends StatelessWidget {
  final VoidCallback onRetry;

  const NoInternetView({super.key, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: AppConfig.backgroundColor,
      width: double.infinity,
      padding: const EdgeInsets.all(28),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            Icons.wifi_off_rounded,
            size: 76,
            color: AppConfig.primaryColor,
          ),
          const SizedBox(height: 20),
          const Text(
            'لا يوجد اتصال بالإنترنت',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 10),
          Text(
            'تأكد من الاتصال ثم اضغط إعادة المحاولة.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: Colors.white.withOpacity(.70),
              height: 1.6,
              fontSize: 15,
            ),
          ),
          const SizedBox(height: 22),
          FilledButton.icon(
            style: FilledButton.styleFrom(
              backgroundColor: AppConfig.primaryColor,
              minimumSize: const Size(180, 48),
            ),
            onPressed: onRetry,
            icon: const Icon(Icons.refresh_rounded),
            label: const Text('إعادة المحاولة'),
          ),
        ],
      ),
    );
  }
}